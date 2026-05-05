public class ManualNodeTest {
    public static void main(String[] args) throws Exception {
        Node node = new Node();
        node.setNodeName("N:test0");
        node.openPort(20110);

        node.write("D:Juliet-0", "hello from node");

        System.out.println("Node running on port 20110...");
        node.handleIncomingMessages(0);
    }
}
