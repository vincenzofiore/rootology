package app.rootology.ui;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdFilterTest {

    @Test
    void eachRequestHasItsOwnTraceIdInTheLogsAndTheResponse() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        AtomicReference<String> seen = new AtomicReference<>();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.HEADER, "chosen-by-the-client");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> seen.set(MDC.get(TraceIdFilter.KEY)));

        assertThat(seen.get()).isNotBlank().isNotEqualTo("chosen-by-the-client");
        assertThat(response.getHeader(TraceIdFilter.HEADER)).isEqualTo(seen.get());
        assertThat(MDC.get(TraceIdFilter.KEY)).isNull();
    }
}
