package navrelis.dynamiclighting.luminance;

/**
 * Why a data file is not in use. Quiet reasons are normal in a mod pack (a file for a mod that is
 * not installed, an outdated twin of a current file) and are only logged at debug level.
 */
enum SkipReason {
	/** The resource could not be read. */
	UNREADABLE(false),
	/** Not valid JSON. */
	SYNTAX(false),
	/** Valid JSON, but the root is not an object. */
	NOT_AN_OBJECT(false),
	/** The format from before {@code match} existed: a top-level {@code item}. */
	LEGACY_SHAPE(true),
	/** {@code fabric:load_conditions} evaluated to false. */
	CONDITIONS_NOT_MET(true),
	/** {@code fabric:load_conditions} could not be decoded or tested. */
	BAD_CONDITIONS(false),
	/** No {@code match} object. */
	NO_MATCH(false),
	/** A {@code match} that constrains nothing and would apply to everything. */
	EMPTY_MATCH(false),
	/** An entity {@code match} with a key this mod does not evaluate, or without a type. */
	UNSUPPORTED_MATCH(false),
	/** An item, block, entity type or tag that does not exist here. */
	UNKNOWN_ID(true),
	/** A missing, out-of-range or unknown luminance. */
	BAD_LUMINANCE(false),
	/** Any other decoding failure. */
	INVALID(false);

	private final boolean quiet;

	SkipReason(boolean quiet) {
		this.quiet = quiet;
	}

	boolean quiet() {
		return this.quiet;
	}
}
