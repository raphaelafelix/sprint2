import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;

public class ServidorHttp {

    private static Connection con; // Conexão com o Banco de Dados

    // Dando entrada no programa Java
    public static void main(String[] args) throws Exception {

        // Conectar ao SQLite (arquivo conteudo.db na pasta do projeto)
        con = DriverManager.getConnection("jdbc:sqlite:conteudo.db");

        // Criar tabela (se não existir)
        String sql = "CREATE TABLE IF NOT EXISTS dados (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "nome TEXT," +
                "descricao TEXT," +
                "data TEXT," +
                "curtida TEXT" +
                ")";
        // Enviando ao Banco de Dados
        con.createStatement().execute(sql);

        // Criar servidor HTTP
        HttpServer s = HttpServer.create(new InetSocketAddress(8082), 0);

        // Rotas básicas
        s.createContext("/", t -> enviar(t, "login.html"));   // mostra login
        s.createContext("/login", ServidorHttp::login);           // processa login
        s.createContext("/produtor", ServidorHttp::produtor);     // cadastro
        s.createContext("/consumidor", ServidorHttp::consumidor); // lista cards
        s.createContext("/avaliar", ServidorHttp::avaliar);       // curtir / não curtir
        s.createContext("/estilo.css", t -> enviarCSS(t, "estilo.css")); // CSS
        s.createContext("/aluno.png", t -> enviarImagem(t, "aluno.png")); // IMAGEM
        s.createContext("/professor.png", t -> enviarImagem(t, "professor.png")); // IMAGEM
        s.createContext("/atividade.png", t -> enviarImagem(t, "atividade.png")); // IMAGEM


        s.start();
        System.out.println("Servidor rodando em http://localhost:8082/");
    }

    // -------------------- LOGIN --------------------

    private static void login(HttpExchange t) throws IOException {
        if (!t.getRequestMethod().equalsIgnoreCase("POST")) {
            enviar(t, "login.html");
            return;
        } // Nesse comando, SE o método de requisição não for POST (enviar dados), o usuário será redirecionado para a página de Login

        String corpo = ler(t); // exemplo: tipo=produtor
        corpo = URLDecoder.decode(corpo, StandardCharsets.UTF_8);  // Aqui, a URL vai ser decodificada e convertida para leitura humana

        if (corpo.contains("produtor")) {
            redirecionar(t, "/produtor"); // Se o usuário selecionar a opção "Produtor", ele será direcionado para a página "Produtor"
        } else {
            redirecionar(t, "/consumidor");
        } // Se o usuário selecionar outra opção, será redirecionado para a página "Consumidor"
    }

    // -------------------- PRODUTOR --------------------

    private static void produtor(HttpExchange t) throws IOException {

        if (!t.getRequestMethod().equalsIgnoreCase("POST")) {
            enviar(t, "produtor.html");
            return;
        }

        String c = URLDecoder.decode(ler(t), StandardCharsets.UTF_8); // Os dados enviados ao banco de dados vão ser decodificados

        String nome = pega(c, "nome");
        String desc = pega(c, "descricao");
        String data = pega(c, "data");
        // Extrai os valores do banco de dados que foram decodificados e coloca eles em seus respectivos campos

        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO dados (nome, descricao, data, curtida) VALUES (?,?,?,?)")) { // Há pontos de interrogação porque a ordem (ainda) é desconhecida

            ps.setString(1, nome);
            ps.setString(2, desc); // type: text
            ps.setString(3, data); // organizando os valores em seus respectivos parâmetros (type: data)
            ps.setString(4, "nenhuma"); // ainda não curtido
            ps.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace(); // Para escrever no leitor caso algo dê errado dentro
        }

        redirecionar(t, "/produtor"); // Para ser redirecionado até a rota
    }

    // -------------------- CONSUMIDOR (lista todos os cards) --------------------

    private static void consumidor(HttpExchange t) throws IOException {
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>"); // Montando o esqueleto HTML para mostrar as curtidas
        html.append("<html><head>");
        html.append("<meta charset=\"UTF-8\">");
        html.append("<title>Consumidor</title>");
        html.append("<link rel=\"stylesheet\" href=\"/estilo.css\">");
        html.append("</head><body>");

        html.append("<h1>Consumidor</h1>");
        html.append("<p>Cada atividade aparece em um card separado.</p>"); // Funciona de forma semelhante ao HTML comum

        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, nome, descricao, data, curtida FROM dados ORDER BY id DESC")) { // para mostrar os dados obtidos

            boolean vazio = true; // Mostre os dados enquanto "vazio" realmente estiver vazio

            while (rs.next()) {
                vazio = false; // Enquanto não estiver vazio

                int id = rs.getInt("id"); // Extrata o valor da ID e mostre
                String nome = rs.getString("nome");
                String desc = rs.getString("descricao");
                String data = rs.getString("data");
                String curtida = rs.getString("curtida");

                // Classe extra para cor do card
                String classeExtra = "";
                if ("curtir".equals(curtida)) { // Se a postagem for curtida, mostre que o card foi curtido
                    classeExtra = " card-curtido";
                } else if ("nao".equals(curtida)) {
                    classeExtra = " card-nao-curtido";
                }

                html.append("<div class=\"card").append(classeExtra).append("\">");
                html.append("<img src=\"/atividade.png\" width=\"120\">"); // Define o tamanho da imagem
                html.append("<p><strong>ID:</strong> ").append(id).append("</p>");
                html.append("<p><strong>Nome:</strong> ").append(nome).append("</p>");
                html.append("<p><strong>Descrição:</strong> ").append(desc).append("</p>");
                html.append("<p><strong>Data:</strong> ").append(data).append("</p>");
                html.append("<p><strong>Status:</strong> ").append(curtida).append("</p>");

                // Botão CURTIR
                html.append("<form method=\"POST\" action=\"/avaliar\">"); // O método POST é equivalente ao envio de dados ao banco de dados, ou seja, nesse caso ao clicar no botão o usuário está enviando informações ao BD
                html.append("<input type=\"hidden\" name=\"id\" value=\"").append(id).append("\">");
                html.append("<input type=\"hidden\" name=\"acao\" value=\"curtir\">");
                html.append("<button type=\"submit\">Curtir</button>");
                html.append("</form>");

                // Botão NÃO CURTIR
                html.append("<form method=\"POST\" action=\"/avaliar\">");
                html.append("<input type=\"hidden\" name=\"id\" value=\"").append(id).append("\">");
                html.append("<input type=\"hidden\" name=\"acao\" value=\"nao\">");
                html.append("<button type=\"submit\">Não curtir</button>");
                html.append("</form>");

                html.append("</div>"); // A div é para organizar de forma visualmente agradável os cards
            }

            if (vazio) {
                html.append("<p>Nenhuma atividade cadastrada ainda.</p>"); // Se estiver vazio, mostre essa mensagem
            }

        } catch (SQLException e) { // Identifique um erro
            e.printStackTrace();
            html.append("<p>Erro ao carregar atividades.</p>");
        }

        html.append("</body></html>");

        // Enviar HTML gerado
        byte[] b = html.toString().getBytes(StandardCharsets.UTF_8);
        t.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
        t.sendResponseHeaders(200, b.length);
        t.getResponseBody().write(b);
        t.close();
    }

    // -------------------- AVALIAR (curtir / não curtir um card específico) --------------------

    private static void avaliar(HttpExchange t) throws IOException {

        if (!t.getRequestMethod().equalsIgnoreCase("POST")) {
            redirecionar(t, "/consumidor");
            return;
        }

        String corpo = URLDecoder.decode(ler(t), StandardCharsets.UTF_8); // Decodificar
        String acao = pega(corpo, "acao"); // "curtir" ou "nao"
        String idStr = pega(corpo, "id");

        try {
            int id = Integer.parseInt(idStr);

            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE dados SET curtida = ? WHERE id = ?")) {
                ps.setString(1, acao);
                ps.setInt(2, id);
                ps.executeUpdate();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        redirecionar(t, "/consumidor");
    }

    // -------------------- ENVIAR IMAGEM --------------------

    private static void enviarImagem(HttpExchange t, String arquivo) throws IOException {
        File f = new File("src/main/java/" + arquivo);

        byte[] bytes = java.nio.file.Files.readAllBytes(f.toPath());
        t.getResponseHeaders().add("Content-Type", "image/png");
        t.sendResponseHeaders(200, bytes.length);
        t.getResponseBody().write(bytes);
        t.close();
    }


    // -------------------- Funções auxiliares --------------------

    private static String pega(String corpo, String campo) {
        // corpo no formato: campo1=valor1&campo2=valor2...
        for (String s : corpo.split("&")) {
            String[] p = s.split("=");
            if (p.length == 2 && p[0].equals(campo)) return p[1];
        }
        return "";
    }

    private static String ler(HttpExchange t) throws IOException {
        BufferedReader br = new BufferedReader(
                new InputStreamReader(t.getRequestBody(), StandardCharsets.UTF_8)
        );
        String linha = br.readLine();
        return (linha == null) ? "" : linha;
    }

    private static void enviar(HttpExchange t, String arq) throws IOException {
        File f = new File("src/main/java/" + arq);
        byte[] b = java.nio.file.Files.readAllBytes(f.toPath());
        t.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
        t.sendResponseHeaders(200, b.length);
        t.getResponseBody().write(b);
        t.close();
    }

    private static void enviarCSS(HttpExchange t, String arq) throws IOException {
        File f = new File("src/main/java/" + arq);
        byte[] b = java.nio.file.Files.readAllBytes(f.toPath());
        t.getResponseHeaders().add("Content-Type", "text/css; charset=UTF-8");
        t.sendResponseHeaders(200, b.length);
        t.getResponseBody().write(b);
        t.close();
    }

    private static void redirecionar(HttpExchange t, String rota) throws IOException {
        t.getResponseHeaders().add("Location", rota);
        t.sendResponseHeaders(302, -1);
        t.close();
    }
}
