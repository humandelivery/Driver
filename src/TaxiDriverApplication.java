

import java.util.concurrent.CountDownLatch;

import application.ClientService;
import application.TaxiDriverRunner;

public class TaxiDriverApplication {

	public static void main(String[] args) throws Exception {
		System.out.println("TaxiDriverApplication is running...");

		ClientService clientService = new ClientService();
		TaxiDriverRunner runner = new TaxiDriverRunner(clientService);

		runner.run();

		// Latch 걸어놓고 무한 대기
		CountDownLatch latch = new CountDownLatch(1);
		latch.await();
	}
}
