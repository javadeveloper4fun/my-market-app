package ru.yandex.practicum.mymarket.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.config.ImageStorageProperties;

class ImageStorageServiceTest {

    private final ImageStorageService service = new ImageStorageService(null, properties("5MB"));

    @Test
    void contentTypeOf_png() {
        assertThat(service.contentTypeOf("image.png")).isEqualTo(MediaType.IMAGE_PNG);
    }

    @Test
    void contentTypeOf_gif() {
        assertThat(service.contentTypeOf("image.gif")).isEqualTo(MediaType.IMAGE_GIF);
    }

    @Test
    void contentTypeOf_jpg() {
        assertThat(service.contentTypeOf("image.jpg")).isEqualTo(MediaType.IMAGE_JPEG);
    }

    @Test
    void contentTypeOf_jpeg() {
        assertThat(service.contentTypeOf("image.jpeg")).isEqualTo(MediaType.IMAGE_JPEG);
    }

    @Test
    void contentTypeOf_unknownExtension() {
        assertThat(service.contentTypeOf("image.xyz")).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
    }

    @Test
    void readBytes_acceptsFileSmallerThanLimit() {
        ImageStorageService smallLimit = new ImageStorageService(null, properties("10KB"));
        FilePart file = filePart("small.jpg", new byte[9 * 1024], 9L);

        StepVerifier.create(smallLimit.readBytes(file))
                .assertNext(bytes -> assertThat(bytes).hasSize(9 * 1024))
                .verifyComplete();
    }

    @Test
    void readBytes_acceptsFileExactlyAtLimit() {
        ImageStorageService smallLimit = new ImageStorageService(null, properties("10KB"));
        FilePart file = filePart("exact.jpg", new byte[10 * 1024], 10L);

        StepVerifier.create(smallLimit.readBytes(file))
                .assertNext(bytes -> assertThat(bytes).hasSize(10 * 1024))
                .verifyComplete();
    }

    @Test
    void readBytes_rejectsFileLargerThanLimit() {
        ImageStorageService smallLimit = new ImageStorageService(null, properties("10KB"));
        FilePart file = filePart("large.jpg", new byte[11 * 1024], 11L);

        StepVerifier.create(smallLimit.readBytes(file))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ImageUploadException.class);
                    assertThat(error.getMessage()).contains("слишком большое");
                })
                .verify();
    }

    @Test
    void readBytes_rejectsLargeFileWithoutContentLength() {
        ImageStorageService smallLimit = new ImageStorageService(null, properties("10KB"));
        FilePart file = filePart("large.jpg", new byte[11 * 1024], -1L);

        StepVerifier.create(smallLimit.readBytes(file))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ImageUploadException.class);
                    assertThat(error.getMessage()).contains("слишком большое");
                })
                .verify();
    }

    @Test
    void readBytes_rejectsLargeFileWithUnderstatedContentLength() {
        ImageStorageService smallLimit = new ImageStorageService(null, properties("10KB"));
        FilePart file = filePart("large.jpg", new byte[11 * 1024], 2L);

        StepVerifier.create(smallLimit.readBytes(file))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ImageUploadException.class);
                    assertThat(error.getMessage()).contains("слишком большое");
                })
                .verify();
    }

    private static ImageStorageProperties properties(String maxUploadSize) {
        ImageStorageProperties properties = new ImageStorageProperties();
        properties.setEndpoint("http://localhost:9090");
        properties.setBucket("test-bucket");
        properties.setRegion("us-east-1");
        properties.setAccessKey("test");
        properties.setSecretKey("test");
        properties.setMaxUploadSize(maxUploadSize);
        return properties;
    }

    private static FilePart filePart(String filename, byte[] bytes, long contentLength) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_JPEG);
        if (contentLength >= 0) {
            headers.setContentLength(contentLength);
        }
        return new TestFilePart("image", filename, headers, bytes);
    }

    private static final class TestFilePart implements FilePart {
        private final String name;
        private final String filename;
        private final HttpHeaders headers;
        private final byte[] bytes;

        private TestFilePart(String name, String filename, HttpHeaders headers, byte[] bytes) {
            this.name = name;
            this.filename = filename;
            this.headers = HttpHeaders.readOnlyHttpHeaders(headers);
            this.bytes = bytes;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String filename() {
            return filename;
        }

        @Override
        public HttpHeaders headers() {
            return headers;
        }

        @Override
        public Flux<DataBuffer> content() {
            return Flux.defer(() -> {
                DefaultDataBufferFactory factory = DefaultDataBufferFactory.sharedInstance;
                DataBuffer buffer = factory.allocateBuffer(bytes.length);
                buffer.write(bytes);
                return Mono.just(buffer);
            });
        }

        @Override
        public Mono<Void> transferTo(Path dest) {
            return Mono.error(new UnsupportedOperationException("not needed in tests"));
        }
    }
}
