package navrelis.dynamiclighting.luminance;

import java.util.List;

import net.minecraft.resources.ResourceLocation;

/**
 * The outcome of decoding all data files against the current registries and tags.
 *
 * @param itemRules   active item rules
 * @param entityRules active entity rules
 * @param skips       one entry per file that is not in use, with the reason
 */
record CompiledData(List<ItemRule> itemRules, List<EntityRule> entityRules, List<Skip> skips) {
	static final CompiledData EMPTY = new CompiledData(List.of(), List.of(), List.of());

	CompiledData {
		itemRules = List.copyOf(itemRules);
		entityRules = List.copyOf(entityRules);
		skips = List.copyOf(skips);
	}

	/** Number of files skipped for the given reason. */
	int skipped(SkipReason reason) {
		int count = 0;

		for (Skip skip : this.skips) {
			if (skip.reason() == reason) {
				count++;
			}
		}

		return count;
	}

	/** Number of skipped files that deserve a warning. */
	int warnings() {
		int count = 0;

		for (Skip skip : this.skips) {
			if (!skip.quiet()) {
				count++;
			}
		}

		return count;
	}

	/**
	 * A file that is not in use.
	 *
	 * @param file   resource id of the file
	 * @param reason why it is skipped
	 * @param detail message for the log
	 * @param quiet  log at debug level only: the reason is harmless or the file asked for silence
	 */
	record Skip(ResourceLocation file, SkipReason reason, String detail, boolean quiet) {
	}
}
