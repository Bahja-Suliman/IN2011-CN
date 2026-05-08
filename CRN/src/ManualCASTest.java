public class ManualCASTest {
    public static void main(String[] args) throws Exception {

        Node target = new Node();
        target.setNodeName("N:cas-target");
        target.openPort(20110);

        target.write("D:cas-test", "old");

        Node client = new Node();
        client.setNodeName("N:cas-client");
        client.openPort(20114);

        client.write("N:cas-target", "127.0.0.1:20110");

        Thread targetThread = new Thread(() -> {
            try {
                target.handleIncomingMessages(0);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        targetThread.start();

        Thread.sleep(500);

        System.out.println("CAS replace old -> new");
        boolean success1 = client.CAS("D:cas-test", "old", "new");
        System.out.println("CAS result 1 = " + success1);

        System.out.println("CAS replace wrong -> final");
        boolean success2 = client.CAS("D:cas-test", "wrong", "final");
        System.out.println("CAS result 2 = " + success2);
    }
}