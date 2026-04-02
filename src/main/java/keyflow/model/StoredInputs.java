package keyflow.model;

import java.io.Serializable;

public record StoredInputs(StoredUpload file1, StoredUpload file2) implements Serializable {
}
