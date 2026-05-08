public class ManualRelayTest {

    public static void main(String[] args) throws Exception {

        // target node
        Node target = new Node();
        target.setNodeName("N:test0");
        target.openPort(20110);

        target.write("D:relay-test", "hello");

        // relay node
        Node relay = new Node();
        relay.setNodeName("N:relay");
        relay.openPort(20112);

        relay.write("N:test0", "127.0.0.1:20110");

        // client node
        Node client = new Node();
        client.setNodeName("N:client");
        client.openPort(20114);

        client.write("N:relay", "127.0.0.1:20112");
        client.write("N:test0", "127.0.0.1:20110");

        client.pushRelay("N:relay");

        // give nodes time
        Thread.sleep(1000);

        String value = client.read("D:relay-test");

        System.out.println("Relay result: " + value);

        // keep alive for Wireshark
        while (true) {
            target.handleIncomingMessages(50);
            relay.handleIncomingMessages(50);
            client.handleIncomingMessages(50);
        }
    }
}