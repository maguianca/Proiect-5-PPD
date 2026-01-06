package ro.ppd2025;

import ro.ppd2025.Request;
import ro.ppd2025.Response;
import ro.ppd2025.VehicleType;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import ro.ppd2025.Config;
public class Server {
    // --- RESURSE PARTAJATE ---
    private static final AtomicLong ID_GEN = new AtomicLong(0);
    // Stoc: Map<DepozitID, Map<TipVehicul, NumarAtomic>>
    private static final Map<Integer, Map<VehicleType, AtomicInteger>> inventory = new ConcurrentHashMap<>();
    // Comenzi active
    private static final Map<Long, InternalOrder> orders = new ConcurrentHashMap<>();

    // Executor pentru logica de alocare (Future requirement)

    private static final ExecutorService allocationExecutor =
            Executors.newFixedThreadPool(Config.getAllocationPoolSize());

    private static final String ORDERS_FILE = "orders.txt";
    private static final String CONTRACTS_FILE = "contracts.txt";
    private static final String CANCELLATIONS_FILE = "cancellations.txt";
    private static final Map<Integer, Map<VehicleType, Integer>> initialInventory = new ConcurrentHashMap<>();
    private static volatile boolean serverRunning = true;
    private static final ReentrantLock ordersLock = new ReentrantLock();
    private static final ReentrantLock contractsLock = new ReentrantLock();
    private static final ReentrantLock cancellationsLock = new ReentrantLock();
    private static final Map<Integer, Double> accumulatedPenalties = new ConcurrentHashMap<>();
    private static final String HISTORY_FILE = "order_history.txt";
    private static final ReentrantLock historyLock = new ReentrantLock();
    public static void main(String[] args) throws IOException {
        System.out.println(">>> Server Logistic Pornit...");
        initializeResources();

        // 1. Thread Pool Principal (Clienți)
        ExecutorService clientPool = Executors.newFixedThreadPool(Config.getThreadPoolSize());

        // 2. Scheduler Audit
        ScheduledExecutorService auditScheduler = Executors.newSingleThreadScheduledExecutor();
        auditScheduler.scheduleAtFixedRate(new AuditTask(), Config.getAuditIntervalSec(),Config.getAuditIntervalSec(), TimeUnit.SECONDS);

        // 3. Thread Oprire Automată (după 3 min)
        new Thread(() -> {
            try {
                Thread.sleep(TimeUnit.MINUTES.toMillis(Config.getServerRuntimeMinutes()));
                System.out.println("\n>>> TIMP EXPIRAT. SERVERUL SE INCHIDE.");
                serverRunning = false;
                // Închide thread pools
                clientPool.shutdown();
                auditScheduler.shutdown();
                allocationExecutor.shutdown();

                // Așteaptă finalizarea task-urilor (max 10 secunde)
                if (!clientPool.awaitTermination(10, TimeUnit.SECONDS)) {
                    clientPool.shutdownNow();
                }

                System.out.println("✅ Server închis corect.");
                System.exit(0);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();

        // 4. Server Loop
        try (ServerSocket serverSocket = new ServerSocket(Config.getServerPort())) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                clientPool.submit(new ClientHandler(clientSocket));
            }
        }
    }

    private static void initializeResources() {
        Random rand = new Random();
        for (int i = 1; i <= Config.getDepotCount(); i++) {
            Map<VehicleType, AtomicInteger> stock = new ConcurrentHashMap<>();
            Map<VehicleType, Integer> initialStock = new HashMap<>(); // ← ADAUGĂ AICI

            for (VehicleType vt : VehicleType.values()) {
                int count = Config.getInitialVehiclesMin() +
                        rand.nextInt(Config.getInitialVehiclesMax() - Config.getInitialVehiclesMin()+1);
                stock.put(vt, new AtomicInteger(count));
                initialStock.put(vt, count); // ← SALVEAZĂ STOCUL INIȚIAL
            }
            accumulatedPenalties.put(i, 0.0);
            inventory.put(i, stock);
            initialInventory.put(i, initialStock); // ← SALVEAZĂ AICI
            System.out.println("Depozit D" + i + " initializat cu stocuri: " + initialStock);
        }
    }
    private static int getInitialStock(int depotId, VehicleType vt) {
        return initialInventory.get(depotId).get(vt);
    }

    // --- LOGICA CLIENT ---
    static class ClientHandler implements Runnable {
        private final Socket socket;

        public ClientHandler(Socket socket) { this.socket = socket; }

        @Override
        public void run() {
            try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                while (serverRunning) {  // ← SCHIMBAT: Acum verifică flag-ul
                    try {
                        Request req = (Request) in.readObject();

                        // Verifică dacă serverul s-a oprit între timp
                        if (!serverRunning) {
                            out.writeObject(new Response(false, "Server shutting down", 0, 0));
                            out.flush();
                            break;
                        }

                        Response resp = handleRequest(req);
                        out.writeObject(resp);
                        out.flush();
                    } catch (EOFException e) {
                        break;
                    }
                }

                // Notifică clientul că serverul se închide (dacă mai e conectat)
                if (!serverRunning) {
                    try {
                        out.writeObject(new Response(false, "Server is closing", 0, 0));
                        out.flush();
                    } catch (IOException ignored) {
                        // Clientul s-a deconectat deja
                    }
                }

            } catch (Exception e) {
                // Client disconnected
            }
        }

        private Response handleRequest(Request req) throws ExecutionException, InterruptedException {
            if (req.type == Request.Type.RESERVE) {
                // CERINȚA: Folosire FUTURE pentru alocare
                Future<Response> allocationFuture = allocationExecutor.submit(() -> performReservation(req));
                return allocationFuture.get(); // Așteptăm rezultatul
            }
            else if (req.type == Request.Type.CONFIRM) {
                return performConfirmation(req);
            }
            return new Response(false, "Unknown command", 0, 0);
        }

        // Logică Alocare
        private Response performReservation(Request req) {
            Map<VehicleType, AtomicInteger> depotStock = inventory.get(req.depotId);
            if (depotStock == null) return new Response(false, "Depozit invalid", 0, 0);
            if (req.weight > req.vehicleType.maxCapacity) {
                return new Response(false, "Greutate depășește capacitatea vehiculului!", 0, 0);
            }
            AtomicInteger count = depotStock.get(req.vehicleType);
            // Decrementare atomică (CAS Loop)
            while (true) {
                int current = count.get();
                if (current <= 0) return new Response(false, "Nu sunt vehicule disponibile", 0, 0);
                if (count.compareAndSet(current, current - 1)) break;
            }

            long id = ID_GEN.incrementAndGet();
            double val = req.weight * req.vehicleType.costPerKm * 100; // Distanta fictiva 100km
            InternalOrder order = new InternalOrder(id, req.clientId, req.depotId, req.vehicleType, req.weight, val,req.cnp);
            orders.put(id, order);
            saveOrder(order);
            saveHistory(id, "REZERVAT");
            System.out.println("[REZERVARE] ID:" + id + " Pt: Client " + req.clientId);
            return new Response(true, "Rezervat", id, val);
        }

        // Logică Confirmare
        private Response performConfirmation(Request req) {
            InternalOrder order = orders.get(req.orderId);
            if (order == null) return new Response(false, "Comandă inexistentă", 0, 0);
            // Sincronizare pe obiectul order pentru a nu intra în conflict cu Auditul
            synchronized (order) {
                if (orders.get(req.orderId) == null) {
                    return new Response(false, "Comanda a fost anulată între timp", req.orderId, 0);
                }
                if (System.currentTimeMillis() - order.reservationTime > Config.getTimeoutSignMs()) {
                    return new Response(false, "Timp expirat! Rezervare anulată.", req.orderId, 0);
                }
                order.confirmed = true;
                saveContract(order);
                saveHistory(req.orderId, "CONFIRMAT_CONTRACT");
            }
            System.out.println("[CONFIRMARE] ID:" + req.orderId + " Valoare:" + order.value);
            return new Response(true, "Confirmat", req.orderId, order.value);
        }
    }

    static class AuditTask implements Runnable {
        @Override
        public void run() {
            StringBuilder log = new StringBuilder();
            log.append("\n--- AUDIT ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_TIME)).append(" ---\n");

            long now = System.currentTimeMillis();
            // 1. SNAPSHOT: Facem o copie a valorilor în acest moment
            // Asta e rapid (copiază doar referințele) și ne protejează de modificări structurale
            List<InternalOrder> snapshot = new ArrayList<>(orders.values());

            // Acum folosim 'snapshot' pentru tot restul metodei, NU 'orders.values()'

            // Pasul A: Validare (folosind snapshot)
            validateResources(log, snapshot);
            //validateResources(log);
            //Map pentru venituri per depozit
            Map<Integer, Double> currentActiveRevenue = new HashMap<>();
            Map<Integer, List<Long>> unconfirmedPerDepot = new HashMap<>();

            // Inițializare depozite
            for (int depotId : inventory.keySet()) {
                currentActiveRevenue.put(depotId, 0.0);
                unconfirmedPerDepot.put(depotId, new ArrayList<>());
            }

            // 1. Curățare Expirate + Calculare Venituri
            for (InternalOrder order : snapshot) {
                synchronized (order) {
                    if (!order.confirmed) {
                        if (now - order.reservationTime > Config.getTimeoutSignMs()) {
                            // Expirat -> Returnează vehicul
                            double penalty = order.value * 0.1; // 10% penalizare (exemplu)
                            // Adăugăm atomic penalizarea în istoricul global
                            accumulatedPenalties.merge(order.depotId, penalty, Double::sum);
                            inventory.get(order.depotId).get(order.vehicleType).incrementAndGet();
                            saveCancellation(order);
                            orders.remove(order.id);
                            saveHistory(order.id, "ANULAT_EXPIRAT");
                            log.append("EXPIRAT: Comanda ").append(order.id).append(". Vehicul eliberat.\n");
                        } else {
                            //SCHIMBARE 3: Adaugă la lista depozitului specific
                            unconfirmedPerDepot.get(order.depotId).add(order.id);
                        }
                    } else {
                        // SCHIMBARE 4: Adună venitul la depozitul corespunzător
                        currentActiveRevenue.merge(order.depotId, order.value, Double::sum);
                    }
                }
            }

            // 2. Raportare PER DEPOZIT
            log.append("\n📊 VENITURI PER DEPOZIT:\n");
            for (Integer depotId: inventory.keySet()) {
                double activeRev = currentActiveRevenue.get(depotId);
                double totalPenalties = accumulatedPenalties.get(depotId);

                // FORMULA DIN CERINȚĂ: Confirmate - Penalizări
                double netRevenue = activeRev - totalPenalties;

                List<Long> unconfirmed = unconfirmedPerDepot.get(depotId);

                log.append(String.format("Depozit D%d: Net=%.2f (Activ: %.2f - Penalizări: %.2f) | În așteptare: %s\n",
                        depotId, netRevenue, activeRev, totalPenalties, unconfirmed));
            }

            // 3. Scriere în fișier
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(Config.getAuditFile(), true))) {
                writer.write(log.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }

            System.out.println("✅ Audit rulat. Log salvat în " + Config.getAuditFile());
        }
        // În AuditTask
        private void validateResources(StringBuilder log,List<InternalOrder> snapshot) {
            log.append("\n🔍 VALIDARE RESURSE:\n");
            for (Map.Entry<Integer, Map<VehicleType, AtomicInteger>> depot : inventory.entrySet()) {
                for (Map.Entry<VehicleType, AtomicInteger> vehicle : depot.getValue().entrySet()) {
                    int depotId = depot.getKey();
                    VehicleType vt = vehicle.getKey();
                    int available = vehicle.getValue().get();

                    // Numără vehicule în uz
                    /*long inUse = orders.values().stream()
                            .filter(o -> o.depotId == depotId && o.vehicleType == vt)
                            .count();*/
                    long inUse = snapshot.stream()
                            .filter(o -> o.depotId == depotId && o.vehicleType == vt)
                            .count();

                    int total = available + (int)inUse;
                    int initialStock = getInitialStock(depotId, vt); // Salvează inițial

                    if (total > initialStock) {
                        String error = String.format("❌ EROARE: Overflow la D%d, %s (Total:%d > Inițial:%d)\n",
                                depotId, vt, total, initialStock);
                        log.append(error);
                        System.err.println(error);
                    } else {
                        log.append(String.format("✅ D%d-%s: Disponibil=%d | În uz=%d | Total=%d/%d\n",
                                depotId, vt, available, inUse, total, initialStock));
                    }
                }
            }
        }

    }
    private static synchronized void saveContract(InternalOrder order) {
        contractsLock.lock();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(CONTRACTS_FILE, true))) {
            String line = String.format("%d,%s,%s,%d,%s,%.2f\n",
                    order.id,
                    order.cnp,
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    order.depotId,
                    order.vehicleType,
                    order.value);
            writer.write(line);
        } catch (IOException e) {
            e.printStackTrace();
        }finally{
            contractsLock.unlock();
        }
    }
    private static synchronized void saveCancellation(InternalOrder order) {
        cancellationsLock.lock();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(CANCELLATIONS_FILE, true))) {
            double penalty = order.value * 0.1; // Penalizare 10%
            String line = String.format("%d,%s,%s,%d,%s,%.2f\n",
                    order.id,
                    order.cnp,
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    order.depotId,
                    order.vehicleType,
                    penalty);
            writer.write(line);
        } catch (IOException e) {
            e.printStackTrace();
        }finally{
            cancellationsLock.unlock();
        }
    }
    private static synchronized void saveOrder(InternalOrder order) {
        ordersLock.lock();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(ORDERS_FILE, true))) {
            String line = String.format("%d,%d,%s,%s,%d,%s,%d,REZERVAT,%s\n",
                    order.id,
                    order.clientId,
                    order.cnp,
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    order.depotId,
                    order.vehicleType,
                    order.weight,
                    order.reservationTime);
            writer.write(line);
        } catch (IOException e) {
            e.printStackTrace();
        }finally{
            ordersLock.unlock();
        }
    }
    private static void saveHistory(long orderId, String status) {
        historyLock.lock();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(HISTORY_FILE, true))) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            // Format: ID, DATA, STATUS_NOU
            writer.write(String.format("%d,%s,%s\n", orderId, timestamp, status));
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            historyLock.unlock();
        }
    }

}