package ro.ppd2025;

import ro.ppd2025.Request;
import ro.ppd2025.Response;
import ro.ppd2025.VehicleType;

import java.io.*;
import java.net.Socket;
import java.util.Random;

public class Client {

    public static void main(String[] args) {
        String host = "localhost";
        int port = Config.getServerPort();
        Random rand = new Random();

        // Fiecare rulare a main-ului simuleaza un client nou care face actiuni repetate
        int clientId = rand.nextInt(10000);
        System.out.println("Client " + clientId + " pornit.");

        try (Socket socket = new Socket(host, port);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            // Trimitem cereri continue la intervale de 3 secunde (conform cerință)
            while (true) {
                // 1. Generare date aleatorii

                int depotId = 1 + rand.nextInt(Config.getDepotCount());
                VehicleType vt = VehicleType.values()[rand.nextInt(VehicleType.values().length)];

                int weight = 1 + rand.nextInt(vt.maxCapacity);
                String cnp = "CNP" + clientId + System.currentTimeMillis();

                // 2. Trimite REZERVARE
                Request req = new Request(clientId, depotId, vt, weight, cnp);
                out.writeObject(req);
                out.flush();

                Response resp = (Response) in.readObject();

                if (resp.success) {
                    System.out.println("Comanda " + resp.orderId + " REZERVATA. Astept confirmare...");

                    // 3. Decizie aleatorie: Confirmă sau Lasă să expire
                    // Timp de semnătură e 6000ms.
                    // Random sleep între 2000ms și 8000ms.
                    int sleepTime = Config.getClientConfirmDelayMinMs() +
                            rand.nextInt(Config.getClientConfirmDelayMaxMs() -
                                    Config.getClientConfirmDelayMinMs());
                    Thread.sleep(sleepTime);

                    // Încercare confirmare
                    Request confirmReq = new Request(resp.orderId,cnp);
                    out.writeObject(confirmReq);
                    out.flush();

                    Response confirmResp = (Response) in.readObject();
                    System.out.println("Rezultat final comanda " + resp.orderId + ": " + confirmResp.message);
                } else {
                    System.out.println("Esec rezervare: " + resp.message);
                }

                System.out.println("------------------------------------------------");
                Thread.sleep(Config.getClientRequestIntervalSec() * 1000);
            }

        } catch (Exception e) {
            System.out.println("Serverul s-a inchis sau eroare conexiune.");
        }
    }
}