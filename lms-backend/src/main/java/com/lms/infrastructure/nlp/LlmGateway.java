package com.lms.infrastructure.nlp;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Port to the locally hosted language model.
 *
 * <p>Every method degrades instead of throwing when the model is unavailable,
 * busy or returns something unusable: callers get an empty result and use
 * their classical fallback. That keeps "the model is down" an ordinary state
 * rather than an error the user sees.
 */
public interface LlmGateway {

    /** Whether the model server answered its last health check. */
    boolean available();

    /** The model name, for labelling output. */
    String modelName();

    /**
     * One non-streaming call whose output is constrained to {@code jsonSchema}
     * and parsed into {@code type}.
     *
     * @return empty when the model is unavailable, busy, timed out, or the
     *         output did not parse
     */
    <T> Optional<T> structured(String system, String user, Map<String, Object> jsonSchema, Class<T> type);

    /**
     * As {@link #structured(String, String, Map, Class)}, stopping after
     * {@code maxTokens} output tokens. On a CPU, output length is most of a
     * call's time, so a cap keeps a rambling answer from holding the model for
     * minutes; output cut off mid-JSON simply fails to parse.
     */
    default <T> Optional<T> structured(String system, String user, Map<String, Object> jsonSchema, Class<T> type,
                                       int maxTokens) {
        return structured(system, user, jsonSchema, type);
    }

    /**
     * A streaming chat completion.
     *
     * @param onToken receives each text fragment as it arrives
     * @return the whole answer, or empty when the model could not be used (in
     *         which case {@code onToken} was never called)
     */
    Optional<String> stream(String system, List<Message> messages, Consumer<String> onToken);

    record Message(String role, String content) {
        public static Message user(String content)      { return new Message("user", content); }
        public static Message assistant(String content) { return new Message("assistant", content); }
    }
}
