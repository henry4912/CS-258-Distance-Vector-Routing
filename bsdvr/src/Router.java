import java.io.*;
import java.net.*;
import java.util.*;

public class Router {
    public static void main(String[] args) throws IOException {
        final int PORT = 8888;
        Socket s = new Socket("localhost", PORT);
        InputStream instream = s.getInputStream();
        OutputStream outstream = s.getOutputStream();
        Scanner in = new Scanner(instream);
    }
}