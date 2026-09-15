package luowei.refugee.blueprint;

/**
 * 客户端上传蓝图文件的分片上限。服务端包体仍受原版自定义载荷限制，必须切片。
 */
public final class BlueprintUpload {
	public static final int CHUNK_SIZE = 30_000;
	public static final int MAX_BYTES = 1_500_000;
	public static final int MAX_CHUNKS = (MAX_BYTES + CHUNK_SIZE - 1) / CHUNK_SIZE;

	private BlueprintUpload() {
	}

	public static int chunkCount(int totalBytes) {
		if (totalBytes <= 0) {
			return 0;
		}
		return (totalBytes + CHUNK_SIZE - 1) / CHUNK_SIZE;
	}
}
