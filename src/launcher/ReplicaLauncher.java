package launcher;

import ui.ReplicaFX;

public class ReplicaLauncher {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java ReplicaLauncher <replicaId>");
            System.exit(1);
        }

        int id = Integer.parseInt(args[0]);
        ReplicaFX.launchReplica(id);
    }
}
