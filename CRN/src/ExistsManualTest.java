public class ExistsManualTest {
    public static void main(String[] args) throws Exception {
        Node node = new Node();

        node.setNodeName("N:bahja.exists.test");
        node.openPort(20110);

        // Start another node locally to answer the E request
        Node node2 = new Node();
        node2.setNodeName("N:bahja.exists.answer");
        node2.openPort(20113);

        node2.write("D:test-exists", "hello");

        node.write("N:bahja.exists.answer", "127.0.0.1:20113");

        Thread t = new Thread(() -> {
            try {
                node2.handleIncomingMessages(0);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        t.start();

        boolean found = node.exists("D:test-exists");
        System.out.println("exists(D:test-exists) = " + found);
    }
}