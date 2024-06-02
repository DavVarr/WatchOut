package administrator.client;

import java.util.Scanner;

public class StartClient {
    public static void main(String[] args) {
        Client client = new Client();
        while (true) {
            System.out.println("[CLIENT] Insert a number to execute an operation: ");
            System.out.println("0 - Shutdown the client");
            System.out.println("1 - list of players registered");
            System.out.println("2 - average of the last n heart rate measurements sent to the server by a specific player");
            System.out.println("3 - average of the heart rate sent by all the players to the server and occurred from timestamps t1 and t2");

            Scanner scanner = new Scanner(System.in);
            String input = scanner.next();
            switch (input) {
                case "0":
                    System.out.println("Shutdown client");
                    scanner.close();
                    System.exit(0);
                case "1":
                    client.printPlayers();
                    break;
                case "2":
                    System.out.print("Insert the number of the last n heart rate measurements: ");
                    int n = Integer.parseInt(scanner.next());
                    System.out.print("Insert the player id: ");
                    int player = Integer.parseInt(scanner.next());
                    client.printAverageLastNHR(n, player);
                    break;
                case "3":
                    System.out.print("Insert the timestamp t1 (long): ");
                    long t1 = Long.parseLong(scanner.next());
                    System.out.print("Insert the timestamp t2 (long): ");
                    long t2 = Long.parseLong(scanner.next());
                    client.printAverageRangeHR(t1, t2);
                    break;
                default:
                    System.out.println("Operation not available.");
                    break;
            }
        }
    }
}
