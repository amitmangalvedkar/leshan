package leshan.client.lwm2m.response;

import static org.junit.Assert.*;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import leshan.client.lwm2m.manage.ManageDownlink;
import leshan.client.lwm2m.register.RegisterUplink;
import leshan.client.lwm2m.util.ResponseCallback;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import ch.ethz.inf.vs.californium.coap.CoAP.ResponseCode;
import ch.ethz.inf.vs.californium.coap.LinkFormat;
import ch.ethz.inf.vs.californium.coap.Request;
import ch.ethz.inf.vs.californium.coap.Response;
import ch.ethz.inf.vs.californium.coap.CoAP.Code;
import ch.ethz.inf.vs.californium.network.CoAPEndpoint;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Request.class)
public class UplinkRequestTest {
	private static final String LOCATION = "/LOCATION";
	private static final int SYNC_TIMEOUT_MS = 2000;
	private static final String SERVER_HOST = "leshan.com";
	private static final int SERVER_PORT = 1234;
	private static final String ENDPOINT_NAME = UUID.randomUUID().toString();
	private final String VALID_REQUEST_PAYLOAD = "</lwm2m>;rt=\"oma.lwm2m\", </lwm2m/1/101>, </lwm2m/1/102>, </lwm2m/2/0>, </lwm2m/2/1>, </lwm2m/2/2>, </lwm2m/3/0>, </lwm2m/4/0>, </lwm2m/5>";
	private String actualResponseLocation;
	private String actualRequest;
	private Code actualCode;
	private ResponseCallback callback;
	private String actualRequestPayload;
	
	@Mock
	private CoAPEndpoint endpoint;
	@Mock
	private Request request;
	@Mock
	private ManageDownlink downlink;
	@Mock
	private Response response;
	private RegisterUplink uplink;
	private InetSocketAddress serverAddress;

	@Before
	public void setUp(){
		serverAddress = InetSocketAddress.createUnresolved(SERVER_HOST, SERVER_PORT);
		
		PowerMockito.mockStatic(Request.class);
		when(Request.newGet()).thenReturn(request);
		when(Request.newPost()).thenReturn(request);
		when(Request.newPut()).thenReturn(request);
		when(Request.newDelete()).thenReturn(request);
		

		doAnswer(new Answer<Void>(){

			@Override
			public Void answer(final InvocationOnMock invocation) throws Throwable {
				final Request request = (Request) invocation.getArguments()[0];
				actualRequest = request.getURI();
				actualCode = request.getCode();
				actualRequestPayload = request.getPayloadString();

				final Response response = new Response(ResponseCode.VALID);
				response.getOptions().setLocationPath(LOCATION.substring(1));

				request.setResponse(response);

				return null;
			}
		}).when(endpoint).sendRequest(any(Request.class));

		uplink = new RegisterUplink(serverAddress, endpoint, downlink);
	}
	
	@Test
	public void testGoodResponse() throws InterruptedException {
		final Map<String, String> parameters = new HashMap<>();
		
		when(request.waitForResponse(any(Long.class))).thenReturn(response);
		when(response.getCode()).thenReturn(ResponseCode.VALID);
		
		final OperationResponse operationResponse = uplink.register(ENDPOINT_NAME, parameters, LinkFormat.parse(VALID_REQUEST_PAYLOAD), SYNC_TIMEOUT_MS);
		
		assertTrue(operationResponse.isSuccess());
	}
	
	@Test
	public void testNullTimeoutResponse() throws InterruptedException {
		final Map<String, String> parameters = new HashMap<>();
		
		when(request.waitForResponse(any(Long.class))).thenReturn(null);
		
		final OperationResponse operationResponse = uplink.register(ENDPOINT_NAME, parameters, LinkFormat.parse(VALID_REQUEST_PAYLOAD), SYNC_TIMEOUT_MS);
		
		assertFalse(operationResponse.isSuccess());
		assertEquals(operationResponse.getResponseCode(), ResponseCode.GATEWAY_TIMEOUT);
	}

}