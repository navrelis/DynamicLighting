package navrelis.dynamiclighting.luminance;

import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * One data file as read in the prepare stage of a resource reload: either its JSON tree or the
 * reason it could not be read. Never modified after creation.
 *
 * @param id      resource id of the file
 * @param kind    item or entity file
 * @param json    the parsed tree, {@code null} if reading failed
 * @param problem why reading failed, {@code null} if it did not
 * @param detail  message for the log if reading failed
 */
record RawFile(ResourceLocation id, DataKind kind, @Nullable JsonElement json, @Nullable SkipReason problem, String detail) {
	static RawFile of(ResourceLocation id, DataKind kind, JsonElement json) {
		return new RawFile(id, kind, json, null, "");
	}

	static RawFile failed(ResourceLocation id, DataKind kind, SkipReason problem, String detail) {
		return new RawFile(id, kind, null, problem, detail);
	}
}
