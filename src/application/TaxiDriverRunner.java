package application;

import java.util.Scanner;

public class TaxiDriverRunner {

    private final ClientService clientService;

    public TaxiDriverRunner(ClientService clientService) {
        this.clientService = clientService;
    }

    public void run() throws Exception {
        Scanner scanner = new Scanner(System.in);

        System.out.print("input id: ");
        String loginId = scanner.nextLine();
        System.out.print("input password: ");
        String password = scanner.nextLine();

        String token = RestTemplateToken.RequestLogin(loginId, password);

        System.out.println("are you there? token " + token);

        clientService.connectWithToken(token);
    }
}
