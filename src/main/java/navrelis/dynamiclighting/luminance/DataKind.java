package navrelis.dynamiclighting.luminance;

/**
 * The two kinds of data files, told apart by their folder below {@code dynamiclights/}.
 */
enum DataKind {
	ITEM("item"),
	ENTITY("entity");

	private final String folder;

	DataKind(String folder) {
		this.folder = folder;
	}

	String folder() {
		return this.folder;
	}
}
