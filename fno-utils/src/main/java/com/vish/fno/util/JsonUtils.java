package com.vish.fno.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.util.JsonRecyclerPools;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * JSON utility methods and virtual-thread-safe {@link ObjectMapper} factory.
 *
 * <h2>Virtual Thread Safety (Jackson + VT memory leak fix):</h2>
 * <p>Standard {@code new ObjectMapper()} uses {@code ThreadLocal<BufferRecycler>} for buffer
 * pooling. This works well for long-lived platform threads but creates excessive memory
 * pressure when used with virtual threads — each VT is a new {@code Thread} instance, so
 * each gets a fresh ~4KB {@code BufferRecycler} via ThreadLocal. At high tick rates (695/sec),
 * the allocation rate outpaced GC collection, causing ~500MB/hr of old-gen pressure in
 * production (Feb 2026). Not a classical leak (objects are SoftReference-wrapped and eventually
 * GC-eligible), but the resulting GC pauses (60-118ms) were unacceptable for tick processing.
 *
 * <p>The {@link #createObjectMapper()} factory uses Jackson 2.16+'s
 * {@link JsonRecyclerPools#sharedBoundedPool()} which replaces ThreadLocal-based caching
 * with a shared concurrent pool. This is safe for both platform and virtual threads.
 *
 * <p>All production {@code ObjectMapper} instances should be created via this factory.
 *
 * @see <a href="https://github.com/FasterXML/jackson-core/issues/919">jackson-core #919</a>
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JsonUtils {

    private static final ObjectMapper mapper = createObjectMapper();

    /**
     * Create a virtual-thread-safe {@link ObjectMapper}.
     *
     * <p>Uses {@link JsonRecyclerPools#sharedBoundedPool()} instead of the default
     * ThreadLocal-based {@code BufferRecycler} pooling. This prevents memory leaks
     * when Jackson serialization runs on virtual threads (each VT is a new Thread,
     * so ThreadLocal creates a new BufferRecycler per VT instead of reusing one).
     *
     * <p>The shared bounded pool maintains a fixed-size pool of {@code BufferRecycler}
     * instances shared across all threads, with CAS-based acquisition — no ThreadLocal,
     * no per-thread allocation, safe for both platform and virtual threads.
     *
     * @return a new ObjectMapper configured for virtual-thread safety
     */
    public static ObjectMapper createObjectMapper() {
        return JsonMapper.builder(
                JsonFactory.builder()
                        .recyclerPool(JsonRecyclerPools.sharedBoundedPool())
                        .build()
        ).build();
    }

    /**
     * Create a virtual-thread-safe {@link ObjectMapper} with indented (pretty-print) output.
     *
     * @return a new ObjectMapper with pretty printing and VT-safe buffer recycling
     * @see #createObjectMapper()
     */
    public static ObjectMapper createIndentedObjectMapper() {
        return JsonMapper.builder(
                JsonFactory.builder()
                        .recyclerPool(JsonRecyclerPools.sharedBoundedPool())
                        .build()
        ).configure(SerializationFeature.INDENT_OUTPUT, true)
         .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
         .build();
    }

    public static String getNonFormattedObject(Object order) {
        try {
            return mapper.writeValueAsString(order);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse object: {}", order, e);
        }
        return "";
    }

    public static String getFormattedObject(Object order) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(order);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse object: {}", order, e);
        }
        return "";
    }
}
