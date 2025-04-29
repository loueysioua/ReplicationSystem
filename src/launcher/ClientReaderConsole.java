package launcher;

import database.TextRepository;
import database.TextEntity;

public class ClientReaderConsole {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java ClientReaderConsole <replicaId>");
            return;
        }

        int replicaId = Integer.parseInt(args[0]);
        TextRepository repo = new TextRepository(replicaId);
        TextEntity last = repo.getLastLine();

        if (last != null) {
            System.out.println("📄 Last line: " + last);
        } else {
            System.out.println("❌ No data found.");
        }
    }
}
