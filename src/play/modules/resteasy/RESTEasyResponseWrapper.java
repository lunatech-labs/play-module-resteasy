package play.modules.resteasy;

import java.io.IOException;
import java.io.OutputStream;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.NewCookie;

import org.jboss.resteasy.spi.HttpResponse;
import org.jboss.resteasy.spi.ResteasyProviderFactory;

import play.mvc.Http;
import play.mvc.Http.Request;
import play.mvc.Http.Response;
import play.mvc.results.Error;

public class RESTEasyResponseWrapper implements HttpResponse {
	
	private Response response;
	private HttpServletResponseHeaders responseHeaders;
	private ResteasyProviderFactory factory;
	private Request request;

	public RESTEasyResponseWrapper(Request request, Response response, ResteasyProviderFactory factory) {
		this.request = request;
		this.response = response;
		responseHeaders = new HttpServletResponseHeaders(response, factory);
		this.factory = factory;
	}

	public void addNewCookie(NewCookie newCookie) {
		Http.Cookie cookie = new Http.Cookie();
		cookie.name = newCookie.getName();
		cookie.value = newCookie.getValue();
		cookie.path = newCookie.getPath();
		cookie.domain = newCookie.getDomain();
		cookie.maxAge = newCookie.getMaxAge();
		cookie.secure = newCookie.isSecure();
		response.cookies.put(cookie.name, cookie);
	}

	public MultivaluedMap<String, Object> getOutputHeaders() {
		return responseHeaders;
	}

	public OutputStream getOutputStream() throws IOException {
		return response.out;
	}

	public int getStatus() {
		return response.status != null ? response.status : 0;
	}

	public boolean isCommitted() {
		// FIXME: how do we know this?
		return false;
	}

	public void reset() {
		response.reset();
		responseHeaders = new HttpServletResponseHeaders(response, factory);
	}

	public void sendError(int status) throws IOException {
		Error error = new play.mvc.results.Error(status, "Internal error");
		error.apply(request, response);
	}

	public void sendError(int status, String text) throws IOException {
		Error error = new play.mvc.results.Error(status, text);
		error.apply(request, response);
	}

	public void setStatus(int status) {
		response.status = status;
	}

}
