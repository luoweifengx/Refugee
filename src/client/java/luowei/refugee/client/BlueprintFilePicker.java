package luowei.refugee.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import luowei.refugee.Refugee;
import luowei.refugee.blueprint.BlueprintUpload;
import luowei.refugee.blueprint.PlayerBlueprints;
import luowei.refugee.config.RefugeeConfig;

/**
 * 用系统原生对话框选择本地 {@code .nbt}，避开 Minecraft 的 AWT headless。
 */
public final class BlueprintFilePicker {
	private BlueprintFilePicker() {
	}

	public static void pickAndUpload() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		Thread thread = new Thread(() -> {
			Path path;
			try {
				path = chooseFile();
			} catch (Exception exception) {
				Refugee.LOGGER.warn("Blueprint file dialog failed", exception);
				client.execute(() -> fail(client, "message.refugee.staff.blueprint.upload.dialog"));
				return;
			}
			client.execute(() -> handlePicked(client, path));
		}, "refugee-blueprint-pick");
		thread.setDaemon(true);
		thread.start();
	}

	private static Path chooseFile() throws Exception {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		if (os.contains("win")) {
			return chooseWindows();
		}
		if (os.contains("mac")) {
			return chooseMac();
		}
		return chooseLinux();
	}

	private static Path chooseWindows() throws Exception {
		Path result = Files.createTempFile("refugee-pick", ".txt");
		Path script = Files.createTempFile("refugee-pick", ".ps1");
		try {
			Files.writeString(script, """
					param($OutFile)
					Add-Type -AssemblyName System.Windows.Forms
					$dialog = New-Object System.Windows.Forms.OpenFileDialog
					$dialog.Filter = 'NBT (*.nbt)|*.nbt|All (*.*)|*.*'
					$dialog.Title = 'Upload blueprint'
					$dialog.Multiselect = $false
					[void][System.Windows.Forms.Application]::EnableVisualStyles()
					if ($dialog.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) {
					  [System.IO.File]::WriteAllText($OutFile, $dialog.FileName, [System.Text.UTF8Encoding]::new($false))
					}
					""", StandardCharsets.UTF_8);
			Process process = new ProcessBuilder(
					"powershell.exe",
					"-NoProfile",
					"-STA",
					"-ExecutionPolicy",
					"Bypass",
					"-File",
					script.toAbsolutePath().toString(),
					result.toAbsolutePath().toString()
			).redirectErrorStream(true).start();
			if (!process.waitFor(120, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				throw new IOException("file dialog timed out");
			}
			if (!Files.isRegularFile(result) || Files.size(result) == 0) {
				return null;
			}
			String picked = Files.readString(result, StandardCharsets.UTF_8).trim();
			return picked.isEmpty() ? null : Path.of(picked);
		} finally {
			Files.deleteIfExists(script);
			Files.deleteIfExists(result);
		}
	}

	private static Path chooseMac() throws Exception {
		Process process = new ProcessBuilder(
				"osascript",
				"-e",
				"POSIX path of (choose file of type {\"nbt\"} with prompt \"Upload blueprint\")"
		).redirectErrorStream(true).start();
		if (!process.waitFor(120, TimeUnit.SECONDS)) {
			process.destroyForcibly();
			throw new IOException("file dialog timed out");
		}
		String picked = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
		if (process.exitValue() != 0 || picked.isEmpty() || picked.startsWith("osascript")) {
			return null;
		}
		return Path.of(picked);
	}

	private static Path chooseLinux() throws Exception {
		Process process = new ProcessBuilder(
				"zenity",
				"--file-selection",
				"--file-filter=*.nbt",
				"--title=Upload blueprint"
		).redirectErrorStream(true).start();
		if (!process.waitFor(120, TimeUnit.SECONDS)) {
			process.destroyForcibly();
			throw new IOException("file dialog timed out");
		}
		String picked = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
		if (process.exitValue() != 0 || picked.isEmpty()) {
			return null;
		}
		return Path.of(picked);
	}

	private static void handlePicked(Minecraft client, Path path) {
		if (path == null || client.player == null) {
			return;
		}
		if (!Files.isRegularFile(path)) {
			fail(client, "message.refugee.staff.blueprint.upload.failed");
			return;
		}
		byte[] bytes;
		try {
			bytes = Files.readAllBytes(path);
		} catch (IOException exception) {
			fail(client, "message.refugee.staff.blueprint.upload.failed");
			return;
		}
		if (bytes.length == 0 || bytes.length > BlueprintUpload.MAX_BYTES) {
			fail(client, "message.refugee.staff.blueprint.upload.too_large");
			return;
		}
		CompoundTag nbt;
		try {
			nbt = PlayerBlueprints.readNbt(bytes);
		} catch (Exception exception) {
			fail(client, "message.refugee.staff.blueprint.upload.invalid");
			return;
		}
		HolderGetter<Block> blocks = client.level == null
				? BuiltInRegistries.BLOCK
				: client.level.registryAccess().lookupOrThrow(Registries.BLOCK);
		PlayerBlueprints.ImportStatus status = PlayerBlueprints.checkNbt(nbt, blocks);
		if (status == PlayerBlueprints.ImportStatus.TOO_LARGE_AXIS) {
			client.player.displayClientMessage(Component.translatable(
					"message.refugee.staff.import.too_large_axis",
					RefugeeConfig.importMaxAxis
			), true);
			return;
		}
		if (status == PlayerBlueprints.ImportStatus.TOO_LARGE_VOLUME) {
			client.player.displayClientMessage(Component.translatable(
					"message.refugee.staff.import.too_large_volume",
					RefugeeConfig.importMaxVolume
			), true);
			return;
		}
		if (status != PlayerBlueprints.ImportStatus.OK) {
			fail(client, "message.refugee.staff.blueprint.upload.invalid");
			return;
		}
		StructureTemplate template = new StructureTemplate();
		template.load(blocks, nbt);
		var box = template.getSize();
		client.setScreen(new ImportNameScreen(
				client.screen,
				PlayerBlueprints.suggestedName(path.getFileName().toString()),
				box.getX(),
				box.getY(),
				box.getZ(),
				bytes
		));
	}

	private static void fail(Minecraft client, String key) {
		if (client.player != null) {
			client.player.displayClientMessage(Component.translatable(key), true);
		}
	}
}
