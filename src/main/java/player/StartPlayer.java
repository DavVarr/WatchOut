package player;

import java.util.Scanner;

public class StartPlayer {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        while (true){
            System.out.println("Insert id of the player");
            int id = scanner.nextInt();
            while(id < 0 ){
                System.out.println("id cannot be negative");
                System.out.println("Insert id of the player");
                id = scanner.nextInt();
            }
            new Player(id,10000+id,"http://localhost:1337");
        }


    }
}
