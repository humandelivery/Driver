package application;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import domain.model.LoginResponse;

public class RestTemplateToken {

	public static String RequestLogin(String loginId, String password) {
		String url = "http://localhost:8080/api/v1/taxi-driver/auth-tokens";

		String jsonBody = String.format("""
			{
			    "loginId": "%s",
			    "password": "%s"
			}
			""", loginId, password);

		System.out.println("put JSON = " + jsonBody);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);

		//요청 생성
		HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);

		//요청 전송
		RestTemplate restTemplate = new RestTemplate();
		ResponseEntity<LoginResponse> response = restTemplate.exchange(
			url,
			HttpMethod.POST,
			request,
			LoginResponse.class
		);

		//응답 처리
		LoginResponse body = response.getBody();
		if (response.getStatusCode() == HttpStatus.OK && body != null) {
			if (body.getToken() != null) {
				return body.getToken();
			} else if (body.getMessage() != null) {
				throw new RuntimeException("받은 에러 메시지: " + body.getMessage());
			}
		}

		throw new RuntimeException("토큰 요청 실패: " + response.getStatusCode());
	}

}
