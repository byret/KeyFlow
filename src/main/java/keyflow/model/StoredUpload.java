package keyflow.model;

import java.io.Serializable;

public record StoredUpload(String fileName, byte[] content) implements Serializable {
}
