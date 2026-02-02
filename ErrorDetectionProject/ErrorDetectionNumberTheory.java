import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.awt.*;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ErrorDetectionNumberTheory {

    static int generateChecksum(int data, int prime) {
        int modulus = Math.abs(prime);
        if (modulus == 0) {
            throw new ArithmeticException("Key/modulus must be non-zero");
        }
        return Math.floorMod(data, modulus);
    }

    static boolean isPrime(int n) {
        if (n <= 1) return false;
        if (n <= 3) return true;
        if (n % 2 == 0 || n % 3 == 0) return false;
        for (int i = 5; (long) i * i <= n; i += 6) {
            if (n % i == 0 || n % (i + 2) == 0) return false;
        }
        return true;
    }

    static VerificationResult verify(int sentData, int prime, int receivedData) {
        int senderChecksum = generateChecksum(sentData, prime);
        int receiverChecksum = generateChecksum(receivedData, prime);
        return new VerificationResult(sentData, prime, receivedData, senderChecksum, receiverChecksum);
    }

    private static void runCli() {
        Scanner sc = new Scanner(System.in);

        System.out.print("Enter data to send: ");
        int data = sc.nextInt();

        System.out.print("Enter key/modulus (non-zero integer): ");
        int prime = sc.nextInt();
        if (prime == 0) {
            System.out.println("Key/modulus cannot be 0.");
            sc.close();
            return;
        }

        int checksum = generateChecksum(data, prime);
        System.out.println("\nSender Checksum: " + checksum);

        System.out.print("\nEnter received data: ");
        int receivedData = sc.nextInt();

        int receivedChecksum = generateChecksum(receivedData, prime);
        System.out.println("Receiver Checksum: " + receivedChecksum);

        if (checksum == receivedChecksum) {
            System.out.println("\nNo Error Detected");
        } else {
            System.out.println("\nError Detected - Data Corrupted");
        }

        sc.close();
    }

    public static void main(String[] args) {
        boolean cli = false;
        boolean server = false;
        int port = 8080;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (Objects.equals(arg, "--cli")) {
                cli = true;
            } else if (Objects.equals(arg, "--server") || Objects.equals(arg, "--web")) {
                server = true;
            } else if (arg != null && arg.startsWith("--port=")) {
                String p = arg.substring("--port=".length());
                Integer parsed = safeParseInt(p);
                if (parsed != null && parsed > 0 && parsed <= 65535) {
                    port = parsed;
                }
            } else if (Objects.equals(arg, "--port") && i + 1 < args.length) {
                Integer parsed = safeParseInt(args[i + 1]);
                if (parsed != null && parsed > 0 && parsed <= 65535) {
                    port = parsed;
                }
                i++;
            }
        }

        if (cli || GraphicsEnvironment.isHeadless()) {
            runCli();
            return;
        }

        if (server) {
            try {
                startWebServer(port);
            } catch (IOException e) {
                System.err.println("Failed to start server: " + e.getMessage());
            }
            return;
        }

        SwingUtilities.invokeLater(() -> {
            setModernLookAndFeel();
            new ErrorDetectionUI().setVisible(true);
        });
    }

    private static void startWebServer(int port) throws IOException {
        final Path webRoot = Paths.get(System.getProperty("user.dir"), "web").toAbsolutePath().normalize();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.setExecutor(Executors.newFixedThreadPool(8));

        server.createContext("/api/verify", new VerifyHandler());
        server.createContext("/api/isPrime", new IsPrimeHandler());
        server.createContext("/api/explain", new ExplainHandler());
        server.createContext("/", new StaticHandler(webRoot));

        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.stop(0);
            } catch (Exception ignored) {
                // no-op
            }
        }));

        String url = "http://localhost:" + port + "/";
        System.out.println("Web UI running at: " + url);
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception ignored) {
            // It's fine if we can't auto-open the browser.
        }

        // Keep the process alive until the user stops it (Ctrl+C).
        try {
            new CountDownLatch(1).await();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    static final class VerifyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            Integer sentData;
            Integer prime;
            Integer receivedData;

            if ("GET".equalsIgnoreCase(method)) {
                QueryParams qp = QueryParams.parse(exchange.getRequestURI().getRawQuery());
                sentData = qp.getInt("sentData");
                prime = qp.getInt("prime");
                receivedData = qp.getInt("receivedData");
            } else if ("POST".equalsIgnoreCase(method)) {
                String body = HttpUtil.readUtf8Body(exchange);
                String contentType = String.valueOf(exchange.getRequestHeaders().getFirst("Content-Type"));

                if (contentType.contains("application/json")) {
                    sentData = JsonUtil.getInt(body, "sentData");
                    prime = JsonUtil.getInt(body, "prime");
                    receivedData = JsonUtil.getInt(body, "receivedData");
                } else {
                    // Accept form-style: sentData=..&prime=..&receivedData=..
                    QueryParams qp = QueryParams.parse(body);
                    sentData = qp.getInt("sentData");
                    prime = qp.getInt("prime");
                    receivedData = qp.getInt("receivedData");
                }
            } else {
                writeJson(exchange, 405, "{\"ok\":false,\"message\":\"Method not allowed\"}");
                return;
            }

            if (sentData == null || prime == null || receivedData == null) {
                writeJson(exchange, 400, "{\"ok\":false,\"message\":\"Missing parameters: sentData, prime, receivedData\"}");
                return;
            }
            if (prime == 0) {
                writeJson(exchange, 400, "{\"ok\":false,\"message\":\"Key/modulus must be non-zero\"}");
                return;
            }

            VerificationResult result = verify(sentData, prime, receivedData);
            String json = "{" +
                    "\"ok\":" + result.isOk() + "," +
                    "\"sentData\":" + result.sentData + "," +
                    "\"prime\":" + result.prime + "," +
                    "\"receivedData\":" + result.receivedData + "," +
                    "\"senderChecksum\":" + result.senderChecksum + "," +
                    "\"receiverChecksum\":" + result.receiverChecksum + "," +
                    "\"message\":\"" + (result.isOk() ? "No Error Detected" : "Error Detected - Data Corrupted") + "\"" +
                    "}";

            writeJson(exchange, 200, json);
        }

        private static void writeJson(HttpExchange exchange, int status, String json) throws IOException {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", "application/json; charset=utf-8");
            headers.set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    static final class IsPrimeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                VerifyHandler.writeJson(exchange, 405, "{\"ok\":false,\"message\":\"Method not allowed\"}");
                return;
            }

            QueryParams qp = QueryParams.parse(exchange.getRequestURI().getRawQuery());
            Integer value = qp.getInt("value");
            if (value == null) {
                VerifyHandler.writeJson(exchange, 400, "{\"ok\":false,\"message\":\"Missing parameter: value\"}");
                return;
            }

            boolean prime = isPrime(value);
            String json = "{" +
                    "\"ok\":true," +
                    "\"value\":" + value + "," +
                    "\"isPrime\":" + prime +
                    "}";
            VerifyHandler.writeJson(exchange, 200, json);
        }
    }

    static final class ExplainHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                VerifyHandler.writeJson(exchange, 405, "{\"ok\":false,\"message\":\"Method not allowed\"}");
                return;
            }

            QueryParams qp = QueryParams.parse(exchange.getRequestURI().getRawQuery());
            Integer data = qp.getInt("data");
            Integer prime = qp.getInt("prime");
            if (data == null || prime == null) {
                VerifyHandler.writeJson(exchange, 400, "{\"ok\":false,\"message\":\"Missing parameters: data, prime\"}");
                return;
            }
            if (prime == 0) {
                VerifyHandler.writeJson(exchange, 400, "{\"ok\":false,\"message\":\"Key/modulus must be non-zero\"}");
                return;
            }

            int modulus = Math.abs(prime);
            int remainder = Math.floorMod(data, modulus);
            int quotient = Math.floorDiv(data, modulus);
            String json = "{" +
                    "\"ok\":true," +
                    "\"data\":" + data + "," +
                    "\"prime\":" + prime + "," +
                    "\"quotient\":" + quotient + "," +
                    "\"remainder\":" + remainder + 
                    "}";
            VerifyHandler.writeJson(exchange, 200, json);
        }
    }

    static final class HttpUtil {
        static String readUtf8Body(HttpExchange exchange) throws IOException {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = exchange.getRequestBody().read(buf)) >= 0) {
                if (n == 0) break;
                baos.write(buf, 0, n);
            }
            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    static final class JsonUtil {
        static Integer getInt(String json, String key) {
            if (json == null || key == null) return null;
            Pattern p = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*(-?\\d+)");
            Matcher m = p.matcher(json);
            if (!m.find()) return null;
            return safeParseInt(m.group(1));
        }
    }

    private static Integer safeParseInt(String s) {
        if (s == null) return null;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static final class StaticHandler implements HttpHandler {
        private final Path webRoot;

        StaticHandler(Path webRoot) {
            this.webRoot = webRoot;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String rawPath = exchange.getRequestURI().getPath();
            String rel = (rawPath == null || rawPath.isEmpty() || "/".equals(rawPath)) ? "/index.html" : rawPath;
            Path target = webRoot.resolve(rel.substring(1)).normalize();

            if (!target.startsWith(webRoot) || Files.isDirectory(target) || !Files.exists(target)) {
                // Fallback to index for SPA-ish navigation.
                target = webRoot.resolve("index.html");
                if (!Files.exists(target)) {
                    byte[] notFound = "Missing web/index.html".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                    exchange.sendResponseHeaders(404, notFound.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(notFound);
                    }
                    return;
                }
            }

            byte[] bytes = Files.readAllBytes(target);
            exchange.getResponseHeaders().set("Content-Type", contentType(target));
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        private static String contentType(Path p) {
            String name = p.getFileName().toString().toLowerCase();
            if (name.endsWith(".html")) return "text/html; charset=utf-8";
            if (name.endsWith(".css")) return "text/css; charset=utf-8";
            if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
            if (name.endsWith(".svg")) return "image/svg+xml";
            return "application/octet-stream";
        }
    }

    static final class QueryParams {
        private final String raw;

        private QueryParams(String raw) {
            this.raw = raw == null ? "" : raw;
        }

        static QueryParams parse(String rawQuery) {
            return new QueryParams(rawQuery);
        }

        String get(String key) {
            if (key == null || key.isEmpty() || raw.isEmpty()) return null;
            String[] parts = raw.split("&");
            for (String part : parts) {
                int idx = part.indexOf('=');
                if (idx <= 0) continue;
                String k = decode(part.substring(0, idx));
                if (!key.equals(k)) continue;
                return decode(part.substring(idx + 1));
            }
            return null;
        }

        Integer getInt(String key) {
            String v = get(key);
            if (v == null || v.trim().isEmpty()) return null;
            try {
                return Integer.parseInt(v.trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }

        private static String decode(String s) {
            try {
                return URLDecoder.decode(s, "UTF-8");
            } catch (Exception ex) {
                return s;
            }
        }
    }

    private static void setModernLookAndFeel() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) {
            // Fallback to default look and feel
        }

        Font base = new Font("Segoe UI", Font.PLAIN, 13);
        UIManager.put("defaultFont", base);
        UIManager.put("Label.font", base);
        UIManager.put("Button.font", base);
        UIManager.put("TextField.font", base);
        UIManager.put("TextArea.font", base);
    }

    static final class VerificationResult {
        final int sentData;
        final int prime;
        final int receivedData;
        final int senderChecksum;
        final int receiverChecksum;

        VerificationResult(int sentData, int prime, int receivedData, int senderChecksum, int receiverChecksum) {
            this.sentData = sentData;
            this.prime = prime;
            this.receivedData = receivedData;
            this.senderChecksum = senderChecksum;
            this.receiverChecksum = receiverChecksum;
        }

        boolean isOk() {
            return senderChecksum == receiverChecksum;
        }
    }

    static final class ErrorDetectionUI extends JFrame {
        private final JTextField dataField = new JTextField();
        private final JTextField primeField = new JTextField();
        private final JTextField receivedField = new JTextField();

        private final JLabel senderChecksumValue = new JLabel("-");
        private final JLabel receiverChecksumValue = new JLabel("-");
        private final JLabel transmittedFrameValue = new JLabel("-");

        private final JLabel statusPill = new JLabel("Step 1: Enter data + key/modulus");
        private Integer computedSenderChecksum = null;

        ErrorDetectionUI() {
            super("Error Detection (Number Theory Modulo Checksum)");
            setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            setMinimumSize(new Dimension(860, 520));
            setLocationRelativeTo(null);

            installIntegerFilter(dataField);
            installIntegerFilter(primeField);
            installIntegerFilter(receivedField);

            setContentPane(buildRoot());
        }

        private JComponent buildRoot() {
            JPanel root = new JPanel(new BorderLayout(16, 16));
            root.setBorder(new EmptyBorder(18, 18, 18, 18));

            root.add(buildHeader(), BorderLayout.NORTH);
            root.add(buildMain(), BorderLayout.CENTER);
            root.add(buildFooter(), BorderLayout.SOUTH);

            updateStatusNeutral();
            return root;
        }

        private JComponent buildHeader() {
            JPanel header = new JPanel(new BorderLayout(12, 12));

            JLabel title = new JLabel("Error Detection using Modulo Checksum");
            title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));

            JLabel subtitle = new JLabel("Flow: Sender -> Channel -> Receiver (checksum = data mod key/modulus)");
            subtitle.setForeground(new Color(90, 90, 90));

            JPanel left = new JPanel();
            left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
            left.setOpaque(false);
            left.add(title);
            left.add(Box.createVerticalStrut(4));
            left.add(subtitle);

            statusPill.setOpaque(true);
            statusPill.setBorder(new CompoundBorder(statusPill.getBorder(), new EmptyBorder(8, 12, 8, 12)));

            header.add(left, BorderLayout.CENTER);
            header.add(statusPill, BorderLayout.EAST);
            return header;
        }

        private JComponent buildMain() {
            JPanel main = new JPanel(new GridBagLayout());
            GridBagConstraints gc = new GridBagConstraints();
            gc.insets = new Insets(0, 0, 0, 0);
            gc.fill = GridBagConstraints.BOTH;
            gc.weightx = 1.0;
            gc.weighty = 1.0;

            gc.gridx = 0;
            gc.gridy = 0;
            gc.weightx = 1.0;
            main.add(buildSenderPanel(), gc);

            gc.gridx = 1;
            gc.weightx = 0.05;
            main.add(buildArrowPanel(), gc);

            gc.gridx = 2;
            gc.weightx = 1.0;
            main.add(buildChannelPanel(), gc);

            gc.gridx = 3;
            gc.weightx = 0.05;
            main.add(buildArrowPanel(), gc);

            gc.gridx = 4;
            gc.weightx = 1.0;
            main.add(buildReceiverPanel(), gc);

            return main;
        }

        private JComponent buildArrowPanel() {
            JLabel arrow = new JLabel("->", SwingConstants.CENTER);
            arrow.setFont(arrow.getFont().deriveFont(Font.BOLD, 22f));
            arrow.setForeground(new Color(120, 120, 120));
            JPanel p = new JPanel(new BorderLayout());
            p.setOpaque(false);
            p.add(arrow, BorderLayout.CENTER);
            return p;
        }

        private JComponent buildSenderPanel() {
            JPanel sender = sectionPanel("1) Sender");

            JPanel form = new JPanel(new GridBagLayout());
            form.setOpaque(false);
            GridBagConstraints gc = new GridBagConstraints();
            gc.insets = new Insets(6, 6, 6, 6);
            gc.fill = GridBagConstraints.HORIZONTAL;
            gc.weightx = 1;

            gc.gridx = 0;
            gc.gridy = 0;
            gc.weightx = 0;
            form.add(new JLabel("Data (integer):"), gc);
            gc.gridx = 1;
            gc.weightx = 1;
            form.add(dataField, gc);

            gc.gridx = 0;
            gc.gridy = 1;
            gc.weightx = 0;
            form.add(new JLabel("Key/modulus:"), gc);
            gc.gridx = 1;
            gc.weightx = 1;
            form.add(primeField, gc);

            gc.gridx = 0;
            gc.gridy = 2;
            gc.weightx = 0;
            form.add(new JLabel("Sender checksum:"), gc);
            gc.gridx = 1;
            gc.weightx = 1;
            senderChecksumValue.setFont(senderChecksumValue.getFont().deriveFont(Font.BOLD));
            form.add(senderChecksumValue, gc);

            gc.gridx = 0;
            gc.gridy = 3;
            gc.weightx = 0;
            form.add(new JLabel("Transmitted frame:"), gc);
            gc.gridx = 1;
            gc.weightx = 1;
            transmittedFrameValue.setForeground(new Color(70, 70, 70));
            form.add(transmittedFrameValue, gc);

            JButton compute = new JButton("Compute checksum");
            compute.addActionListener(e -> computeSenderChecksum());

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            actions.setOpaque(false);
            actions.add(compute);

            sender.add(form, BorderLayout.CENTER);
            sender.add(actions, BorderLayout.SOUTH);
            return sender;
        }

        private JComponent buildChannelPanel() {
            JPanel channel = sectionPanel("2) Channel");

            JPanel form = new JPanel(new GridBagLayout());
            form.setOpaque(false);
            GridBagConstraints gc = new GridBagConstraints();
            gc.insets = new Insets(6, 6, 6, 6);
            gc.fill = GridBagConstraints.HORIZONTAL;
            gc.weightx = 1;

            gc.gridx = 0;
            gc.gridy = 0;
            gc.weightx = 0;
            form.add(new JLabel("Received data:"), gc);
            gc.gridx = 1;
            gc.weightx = 1;
            form.add(receivedField, gc);

            JButton copy = new JButton("Copy sent → received");
            copy.addActionListener(e -> copySentToReceived());

            JButton introduceError = new JButton("Introduce +1 error");
            introduceError.addActionListener(e -> introducePlusOneError());

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            actions.setOpaque(false);
            actions.add(copy);
            actions.add(introduceError);

            JLabel hint = new JLabel("Tip: copy the sent data, then optionally introduce an error.");
            hint.setForeground(new Color(90, 90, 90));

            JPanel bottom = new JPanel();
            bottom.setOpaque(false);
            bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
            bottom.add(actions);
            bottom.add(Box.createVerticalStrut(10));
            bottom.add(hint);

            channel.add(form, BorderLayout.CENTER);
            channel.add(bottom, BorderLayout.SOUTH);
            return channel;
        }

        private JComponent buildReceiverPanel() {
            JPanel receiver = sectionPanel("3) Receiver");

            JPanel form = new JPanel(new GridBagLayout());
            form.setOpaque(false);
            GridBagConstraints gc = new GridBagConstraints();
            gc.insets = new Insets(6, 6, 6, 6);
            gc.fill = GridBagConstraints.HORIZONTAL;
            gc.weightx = 1;

            gc.gridx = 0;
            gc.gridy = 0;
            gc.weightx = 0;
            form.add(new JLabel("Receiver checksum:"), gc);
            gc.gridx = 1;
            gc.weightx = 1;
            receiverChecksumValue.setFont(receiverChecksumValue.getFont().deriveFont(Font.BOLD));
            form.add(receiverChecksumValue, gc);

            JButton verifyBtn = new JButton("Verify");
            verifyBtn.addActionListener(e -> verifyNow());

            JButton resetBtn = new JButton("Reset");
            resetBtn.addActionListener(e -> resetAll());

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            actions.setOpaque(false);
            actions.add(verifyBtn);
            actions.add(resetBtn);

            JTextArea explanation = new JTextArea(
                    "How it works:\n" +
                        "Sender checksum = (sentData mod key/modulus)\n" +
                        "Receiver checksum = (receivedData mod key/modulus)\n" +
                            "If both checksums match → no error detected.");
            explanation.setEditable(false);
            explanation.setOpaque(false);
            explanation.setLineWrap(true);
            explanation.setWrapStyleWord(true);
            explanation.setForeground(new Color(90, 90, 90));

            receiver.add(form, BorderLayout.NORTH);
            receiver.add(explanation, BorderLayout.CENTER);
            receiver.add(actions, BorderLayout.SOUTH);
            return receiver;
        }

        private JPanel sectionPanel(String title) {
            JPanel p = new JPanel(new BorderLayout(8, 12));
            p.setBorder(new CompoundBorder(new TitledBorder(title), new EmptyBorder(10, 10, 10, 10)));
            p.setOpaque(false);
            return p;
        }

        private JComponent buildFooter() {
            JPanel footer = new JPanel(new BorderLayout());
            footer.setOpaque(false);

            JLabel note = new JLabel("CLI mode: run with --cli (keeps your original console flow)");
            note.setForeground(new Color(90, 90, 90));
            footer.add(note, BorderLayout.WEST);

            return footer;
        }

        private void computeSenderChecksum() {
            Integer data = parseIntOrNull(dataField.getText());
            Integer prime = parseIntOrNull(primeField.getText());
            if (data == null || prime == null) {
                showError("Enter both Data and Key/Modulus.");
                return;
            }
            if (prime == 0) {
                showError("Key/modulus must be non-zero.");
                return;
            }

            computedSenderChecksum = ErrorDetectionNumberTheory.generateChecksum(data, prime);
            senderChecksumValue.setText(String.valueOf(computedSenderChecksum));
            transmittedFrameValue.setText("data=" + data + " | checksum=" + computedSenderChecksum);

            updateStatusInfo("Step 2: Set received data (channel)");
        }

        private void copySentToReceived() {
            if (computedSenderChecksum == null) {
                showError("Compute the sender checksum first (Step 1).");
                return;
            }
            receivedField.setText(dataField.getText().trim());
            updateStatusInfo("Step 3: Verify at receiver");
        }

        private void introducePlusOneError() {
            if (computedSenderChecksum == null) {
                showError("Compute the sender checksum first (Step 1).");
                return;
            }
            Integer received = parseIntOrNull(receivedField.getText());
            if (received == null) {
                showError("Enter/copy a Received data value first.");
                return;
            }
            receivedField.setText(String.valueOf(received + 1));
            updateStatusInfo("Step 3: Verify at receiver");
        }

        private void verifyNow() {
            Integer data = parseIntOrNull(dataField.getText());
            Integer prime = parseIntOrNull(primeField.getText());
            Integer receivedData = parseIntOrNull(receivedField.getText());

            if (data == null || prime == null) {
                showError("Enter Data and Key/Modulus (Step 1).");
                return;
            }
            if (prime == 0) {
                showError("Key/modulus must be non-zero.");
                return;
            }
            if (computedSenderChecksum == null) {
                showError("Compute the sender checksum first.");
                return;
            }
            if (receivedData == null) {
                showError("Enter Received data (Step 2).");
                return;
            }

            VerificationResult result = ErrorDetectionNumberTheory.verify(data, prime, receivedData);
            senderChecksumValue.setText(String.valueOf(result.senderChecksum));
            receiverChecksumValue.setText(String.valueOf(result.receiverChecksum));

            if (result.isOk()) {
                updateStatusOk("No Error Detected (checksums match)");
            } else {
                updateStatusBad("Error Detected (data corrupted)");
            }
        }

        private void resetAll() {
            dataField.setText("");
            primeField.setText("");
            receivedField.setText("");
            computedSenderChecksum = null;
            senderChecksumValue.setText("-");
            receiverChecksumValue.setText("-");
            transmittedFrameValue.setText("-");
            updateStatusNeutral();
        }

        private void updateStatusNeutral() {
            statusPill.setText("Step 1: Enter data + key/modulus");
            statusPill.setBackground(new Color(235, 235, 235));
            statusPill.setForeground(new Color(40, 40, 40));
        }

        private void updateStatusInfo(String message) {
            statusPill.setText(message);
            statusPill.setBackground(new Color(224, 240, 255));
            statusPill.setForeground(new Color(10, 60, 110));
        }

        private void updateStatusOk(String message) {
            statusPill.setText(message);
            statusPill.setBackground(new Color(222, 245, 230));
            statusPill.setForeground(new Color(20, 95, 45));
        }

        private void updateStatusBad(String message) {
            statusPill.setText(message);
            statusPill.setBackground(new Color(255, 228, 228));
            statusPill.setForeground(new Color(120, 20, 20));
        }

        private void showError(String message) {
            updateStatusBad(message);
            JOptionPane.showMessageDialog(this, message, "Input Error", JOptionPane.ERROR_MESSAGE);
        }

        private static Integer parseIntOrNull(String text) {
            String t = text == null ? "" : text.trim();
            if (t.isEmpty() || "-".equals(t)) return null;
            try {
                return Integer.parseInt(t);
            } catch (NumberFormatException ex) {
                return null;
            }
        }

        private static void installIntegerFilter(JTextField field) {
            ((AbstractDocument) field.getDocument()).setDocumentFilter(new IntegerDocumentFilter());
        }
    }

    static final class IntegerDocumentFilter extends DocumentFilter {
        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
            if (string == null) return;
            replace(fb, offset, 0, string, attr);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
            String current = fb.getDocument().getText(0, fb.getDocument().getLength());
            String next = new StringBuilder(current).replace(offset, offset + length, text == null ? "" : text).toString();
            if (isValidIntegerEdit(next)) {
                fb.replace(offset, length, text, attrs);
            } else {
                Toolkit.getDefaultToolkit().beep();
            }
        }

        private static boolean isValidIntegerEdit(String s) {
            if (s.isEmpty()) return true;
            if ("-".equals(s)) return true;
            int start = 0;
            if (s.charAt(0) == '-') {
                if (s.length() == 1) return true;
                start = 1;
            }
            for (int i = start; i < s.length(); i++) {
                if (!Character.isDigit(s.charAt(i))) return false;
            }
            return true;
        }
    }
}
