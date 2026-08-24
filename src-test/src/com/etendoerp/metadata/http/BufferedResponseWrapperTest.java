/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright © 2021-2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.http;

import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.etendoerp.metadata.http.StaleObjectTestFixtures.captureResponseOutput;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BufferedResponseWrapper}.
 *
 * <p>{@link ForwarderServletTest} and {@link StaleObjectConflictFilterTest} only ever drive this
 * class through its {@link BufferedResponseWrapper#getWriter()} path (that's how their stubbed
 * chains write a response body), so its {@link BufferedResponseWrapper#getOutputStream()} path,
 * {@code setStatus}/{@code getStatus}, and {@code isCommitted} are exercised directly here
 * instead.</p>
 */
@RunWith(MockitoJUnitRunner.class)
public class BufferedResponseWrapperTest {

    @Mock
    private HttpServletResponse response;

    private BufferedResponseWrapper wrapper;

    /**
     * Creates a fresh {@link BufferedResponseWrapper} around the mocked response for each test.
     */
    @Before
    public void setUp() {
        wrapper = new BufferedResponseWrapper(response);
    }

    /**
     * Bytes written through {@link BufferedResponseWrapper#getOutputStream()}, via both the
     * single-byte and the array overloads, must land in the same in-memory buffer returned by
     * {@link BufferedResponseWrapper#getCapturedBodyAsString()}.
     *
     * @throws IOException if an error occurs during test execution
     */
    @Test
    public void writingThroughOutputStreamShouldBeCaptured() throws IOException {
        ServletOutputStream sos = wrapper.getOutputStream();
        byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);

        sos.write(bytes, 0, bytes.length);
        sos.write('!');

        assertEquals("hello!", wrapper.getCapturedBodyAsString());
    }

    /**
     * {@link BufferedResponseWrapper#getOutputStream()} must lazily create the stream once and
     * return that same instance on every subsequent call.
     *
     * @throws IOException if an error occurs during test execution
     */
    @Test
    public void getOutputStreamShouldReturnSameInstanceOnRepeatedCalls() throws IOException {
        ServletOutputStream first = wrapper.getOutputStream();
        ServletOutputStream second = wrapper.getOutputStream();

        assertSame(first, second);
    }

    /**
     * The buffered stream never has to wait on a real client connection, so it must always
     * report itself ready, and setting a write listener on it must be a no-op rather than fail.
     *
     * @throws IOException if an error occurs during test execution
     */
    @Test
    public void outputStreamShouldAlwaysBeReadyAndIgnoreWriteListener() throws IOException {
        ServletOutputStream sos = wrapper.getOutputStream();

        assertTrue(sos.isReady());
        sos.setWriteListener(new WriteListener() {
            @Override
            public void onWritePossible() {
                // never invoked; the stream is always ready.
            }

            @Override
            public void onError(Throwable t) {
                // never invoked; the stream is always ready.
            }
        });
    }

    /**
     * {@link BufferedResponseWrapper#setStatus(int)} must capture the status, and
     * {@link BufferedResponseWrapper#getStatus()} must return exactly what was captured.
     */
    @Test
    public void setStatusShouldBeCapturedAndReturnedByGetStatus() {
        wrapper.setStatus(HttpStatus.SC_CONFLICT);

        assertEquals(HttpStatus.SC_CONFLICT, wrapper.getStatus());
    }

    /**
     * When nothing ever calls {@code setStatus}, {@link BufferedResponseWrapper#getStatus()}
     * must default to {@code SC_OK}, matching a real servlet response that is never told
     * otherwise.
     */
    @Test
    public void getStatusShouldDefaultToOkWhenNeverSet() {
        assertEquals(HttpServletResponse.SC_OK, wrapper.getStatus());
    }

    /**
     * Nothing captured by this wrapper is ever sent to the real client, so it must report
     * itself as never committed, even after content has been written to it.
     *
     * @throws IOException if an error occurs during test execution
     */
    @Test
    public void isCommittedShouldAlwaysReturnFalseEvenAfterWriting() throws IOException {
        assertFalse(wrapper.isCommitted());

        wrapper.getWriter().write("anything");

        assertFalse(wrapper.isCommitted());
    }

    /**
     * {@link BufferedResponseWrapper#flushToRealResponse()} must replay both the captured status
     * and the body written through {@link BufferedResponseWrapper#getOutputStream()} (as opposed
     * to {@code getWriter()}, which is what every other test in this module exercises) onto the
     * real response, unchanged.
     *
     * @throws IOException if an error occurs during test execution
     */
    @Test
    public void flushToRealResponseShouldReplayStatusAndOutputStreamBody() throws IOException {
        ByteArrayOutputStream realOutput = captureResponseOutput(response);
        wrapper.setStatus(HttpStatus.SC_CONFLICT);
        byte[] bytes = "conflict-body".getBytes(StandardCharsets.UTF_8);
        wrapper.getOutputStream().write(bytes, 0, bytes.length);

        wrapper.flushToRealResponse();

        verify(response).setStatus(HttpStatus.SC_CONFLICT);
        assertEquals("conflict-body", realOutput.toString(StandardCharsets.UTF_8));
    }

    /**
     * When the wrapped response reports no character encoding, the captured body must be
     * decoded as UTF-8.
     *
     * @throws IOException if an error occurs during test execution
     */
    @Test
    public void getCapturedBodyAsStringShouldDefaultToUtf8WhenNoEncodingSet() throws IOException {
        when(response.getCharacterEncoding()).thenReturn(null);

        wrapper.getWriter().write("café");

        assertEquals("café", wrapper.getCapturedBodyAsString());
    }

    /**
     * When the wrapped response reports a specific character encoding, the captured body must
     * be decoded using that encoding rather than the UTF-8 default.
     *
     * @throws IOException if an error occurs during test execution
     */
    @Test
    public void getCapturedBodyAsStringShouldUseWrappedResponsesCharacterEncoding() throws IOException {
        when(response.getCharacterEncoding()).thenReturn("ISO-8859-1");

        wrapper.getWriter().write("cafe");

        assertEquals("cafe", wrapper.getCapturedBodyAsString());
    }
}
