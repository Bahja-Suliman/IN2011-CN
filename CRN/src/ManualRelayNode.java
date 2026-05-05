public class ManualRelayNode {
    public static void main(String[] args) throws Exception {
        Node node = new Node();
        node.setNodeName("N:relay");
        node.openPort(20112);

        node.write("N:test0", "127.0.0.1:20110");

        System.out.println("Relay node running...");
        node.handleIncomingMessages(0);
    }
}
