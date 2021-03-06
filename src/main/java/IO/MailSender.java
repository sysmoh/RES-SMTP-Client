package IO;

import DNS.Helper;
import Model.Config;
import Model.EmailAddress;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.logging.Logger;

public class MailSender {

    private Helper helper   = new Helper();
    private Logger logger   = Logger.getLogger(MailSender.class.getName());

    private Config config;

    public MailSender(Config config) {

        this.config = config;
    }

    public SMTPClient connect(String server) {

        String MXRecord     = helper.getMXRecord(server);
        String SMTPServer   = helper.cleanMXRecord(MXRecord);

        return new SMTPClient(SMTPServer);
    }

    public void sendMail(EmailAddress from, List<EmailAddress> to, String subject, String content) {

        SMTPClient client   = config.isMock()
                ? new SMTPClient(config.getMockServer().getServer(), config.getMockServer().getPort())
                : connect(from.getServer());

        if(!client.SMTPSendMail(from, to, subject, content, config.getEhlo()))
            logger.severe("Couldn't send mail from " + from.getEmail() + " with subject " + subject);
    }

    private class SMTPClient {

        final static int SMTP_PORT       = 25;
        final static String EHLO         = "EHLO ";
        final static String MAIL_FROM    = "MAIL FROM: ";
        final static String RCPT_TO      = "RCPT TO: ";
        final static String DATA         = "DATA";

        private Socket connection;

        private BufferedReader in;

        private PrintWriter out;

        SMTPClient(String host) {

            this(host, SMTP_PORT);
        }

        SMTPClient(String host, int port) {

            try {

                connection  = new Socket(host, port);
                in          = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                out         = new PrintWriter(new OutputStreamWriter(connection.getOutputStream()), true);

                System.out.println("Connected to: " + in.readLine());

            } catch (IOException e) {

                logger.severe(e.getMessage());
            }
        }

        boolean SMTPSendMail(EmailAddress from, List<EmailAddress> to, String subject, String message, String ehlo) {

            try {

                //Shake hand
                System.out.println("[SENDER] start sending mail from " + from.getEmail());
                System.out.println("[SENDER] send ehlo for: " + EHLO + ehlo);
                write(EHLO + ehlo);
                StringBuilder EHLObuilder = new StringBuilder();
                int c;
                while((c = in.read()) != 13)
                    EHLObuilder.append((char)c);
                System.out.println("[SERVER] " + EHLObuilder.toString());

                //Send from
                System.out.println("[SENDER] sending from");
                write(MAIL_FROM + from.getEmail());
                read();

                //Send to
                System.out.println("[SENDER] sending to");
                for(EmailAddress toAddress : to) {
                    write(RCPT_TO + toAddress.getEmail());
                    read();
                }

                //Notify server that we send message
                write(DATA);
                read(); //He says ok yo, go ahead

                //Send headers
                System.out.println("[SENDER] sending headers");
                write("Content-Type: text/plain; charset=utf-8");
                write("From: " + from.getEmail());
                write("To: " + implode(to));
                write("Subject: " + subject);
                write("");

                //Send message
                System.out.println("[SENDER] sending message");
                write(message);
                write("."); //End of message

                //Server says i got this
                read();

                write("QUIT");
                read();
                connection.close();
                System.out.println("[SENDER] Mail from " + from.getEmail() + " sent!");
                return true;

            } catch (Exception e) {

                logger.severe(e.getMessage());
                return false;
            }
        }

        private String implode(List<EmailAddress> addresses) {

            StringBuilder builder = new StringBuilder();

            for(EmailAddress address : addresses) {
                builder.append(address.getEmail());

                if(address != addresses.get(addresses.size() - 1))
                    builder.append(',');
            }

            return builder.toString();
        }

        private void write(String s) {

            out.println(s + (char)13);
            out.flush();
        }

        private void read() throws Exception {

            String response = in.readLine();
            System.out.println("[SERVER] " + response);
        }

    }
}
