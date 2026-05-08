public class ExistsManualTest {

    public static void main(String[] args) throws Exception {
        Node node = new Node();

        node.setNodeName("N:bahja.exists.test");
        node.openPort(20112);

        // Give it one known Azure test node address.
        node.write("N:black", "10.200.51.19:20116");
        node.write("N:magenta", "10.200.51.18:20116");

        System.out.println("Testing existing key...");
        boolean found = node.exists("D:jabberwocky0");
        System.out.println("exists(D:jabberwocky0) = " + found);

        System.out.println("Testing missing key...");
        boolean missing = node.exists("D:not-real-bahja-test");
        System.out.println("exists(D:not-real-bahja-test) = " + missing);
    }
}