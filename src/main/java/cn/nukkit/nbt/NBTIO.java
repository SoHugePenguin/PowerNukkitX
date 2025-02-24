package cn.nukkit.nbt;

import cn.nukkit.api.PowerNukkitDifference;
import cn.nukkit.api.PowerNukkitXOnly;
import cn.nukkit.api.Since;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;
import cn.nukkit.item.PNAlphaItemID;
import cn.nukkit.nbt.stream.FastByteArrayOutputStream;
import cn.nukkit.nbt.stream.NBTInputStream;
import cn.nukkit.nbt.stream.NBTOutputStream;
import cn.nukkit.nbt.stream.PGZIPOutputStream;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.Tag;
import cn.nukkit.utils.ThreadCache;

import java.io.*;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;

/**
 * A Named Binary Tag library for Nukkit Project
 */
@PowerNukkitDifference(since = "1.4.0.0-PN", info = "Fixed resource leaks")
@PowerNukkitDifference(since = "1.4.0.0-PN", info = "It's the caller responsibility to close the provided streams")
@PowerNukkitDifference(since = "1.4.0.0-PN", info = "Fixed output streams not being finished correctly")
@PowerNukkitDifference(since = "1.4.0.0-PN", info = "Added defensive close invocations to byte array streams")
public class NBTIO {

    public static CompoundTag putItemHelper(Item item) {
        return putItemHelper(item, null);
    }

    public static CompoundTag putItemHelper(Item item, Integer slot) {
        CompoundTag tag = new CompoundTag((String) null)
                .putByte("Count", item.getCount())
                .putShort("Damage", item.getDamage());
        int id = item.getId();
        if (id == ItemID.STRING_IDENTIFIED_ITEM) {
            tag.putString("Name", item.getNamespaceId());
        } else {
            tag.putShort("id", item.getId());
        }
        if (slot != null) {
            tag.putByte("Slot", slot);
        }

        if (item.hasCompoundTag()) {
            if (id == ItemID.STRING_IDENTIFIED_ITEM) {
                CompoundTag realCompound = item.getNamedTag().clone().remove("Name");
                if (!realCompound.isEmpty()) {
                    tag.putCompound("tag", realCompound);
                }
            } else {
                tag.putCompound("tag", item.getNamedTag());
            }
        }

        return tag;
    }

    public static Item getItemHelper(CompoundTag tag) {
        if (!tag.containsByte("Count") || !(tag.containsShort("id") || tag.containsString("Name"))) {
            return Item.get(0);
        }

        int damage = !tag.containsShort("Damage") ? 0 : tag.getShort("Damage");
        int amount = tag.getByte("Count");
        Item item;
        if (tag.containsShort("id")) {
            int id = (short) tag.getShort("id");
            item = fixAlphaItem(id, damage, amount);
            if (item == null) {
                try {
                    item = Item.get(id, damage, amount);
                } catch (Exception e) {
                    item = Item.fromString(tag.getString("id"));
                    item.setDamage(damage);
                    item.setCount(amount);
                }
            }
        } else {
            item = Item.fromString(tag.getString("Name"));
            item.setDamage(damage);
            item.setCount(amount);
        }

        Tag tagTag = tag.get("tag");
        if (tagTag instanceof CompoundTag) {
            item.setNamedTag((CompoundTag) tagTag);
        }

        return item;
    }
    
    @SuppressWarnings("deprecation")
    private static Item fixAlphaItem(int id, int damage, int count) {
        PNAlphaItemID badAlphaId = PNAlphaItemID.getBadAlphaId(id);
        if (badAlphaId == null) {
            return null;
        }
        Item recovered = badAlphaId.getMinecraftItemId().get(count);
        if (damage != 0) {
            recovered.setDamage(damage);
        }
        return recovered;
    }

    public static CompoundTag read(File file) throws IOException {
        return read(file, ByteOrder.BIG_ENDIAN);
    }

    public static CompoundTag read(File file, ByteOrder endianness) throws IOException {
        if (!file.exists()) return null;
        try (FileInputStream inputStream = new FileInputStream(file)) {
            return read(inputStream, endianness);
        }
    }

    public static CompoundTag read(InputStream inputStream) throws IOException {
        return read(inputStream, ByteOrder.BIG_ENDIAN);
    }

    public static CompoundTag read(InputStream inputStream, ByteOrder endianness) throws IOException {
        return read(inputStream, endianness, false);
    }

    public static CompoundTag read(InputStream inputStream, ByteOrder endianness, boolean network) throws IOException {
        Tag tag = Tag.readNamedTag(new NBTInputStream(inputStream, endianness, network));
        if (tag instanceof CompoundTag) {
            return (CompoundTag) tag;
        }
        throw new IOException("Root tag must be a named compound tag");
    }

    public static Tag readTag(InputStream inputStream, ByteOrder endianness, boolean network) throws IOException {
        return Tag.readNamedTag(new NBTInputStream(inputStream, endianness, network));
    }

    public static CompoundTag read(byte[] data) throws IOException {
        return read(data, ByteOrder.BIG_ENDIAN);
    }

    public static CompoundTag read(byte[] data, ByteOrder endianness) throws IOException {
        try (InputStream inputStream = new ByteArrayInputStream(data)) {
            return read(inputStream, endianness);
        }
    }

    public static CompoundTag read(byte[] data, ByteOrder endianness, boolean network) throws IOException {
        try (InputStream inputStream = new ByteArrayInputStream(data)) {
            return read(inputStream, endianness, network);
        }
    }

    public static CompoundTag readCompressed(InputStream inputStream) throws IOException {
        return readCompressed(inputStream, ByteOrder.BIG_ENDIAN);
    }

    public static CompoundTag readCompressed(InputStream inputStream, ByteOrder endianness) throws IOException {
        try (InputStream gzip = new GZIPInputStream(inputStream); 
            InputStream buffered = new BufferedInputStream(gzip)) {
            return read(buffered, endianness);
        }
    }

    public static CompoundTag readCompressed(byte[] data) throws IOException {
        return readCompressed(data, ByteOrder.BIG_ENDIAN);
    }

    public static CompoundTag readCompressed(byte[] data, ByteOrder endianness) throws IOException {
        try (InputStream bytes = new ByteArrayInputStream(data); 
             InputStream gzip = new GZIPInputStream(bytes);
             InputStream buffered = new BufferedInputStream(gzip)) {
            return read(buffered, endianness, true);
        }
    }

    public static CompoundTag readNetworkCompressed(InputStream inputStream) throws IOException {
        return readNetworkCompressed(inputStream, ByteOrder.BIG_ENDIAN);
    }

    public static CompoundTag readNetworkCompressed(InputStream inputStream, ByteOrder endianness) throws IOException {
        try (InputStream gzip = new GZIPInputStream(inputStream); 
             InputStream buffered = new BufferedInputStream(gzip)) {
            return read(buffered, endianness);
        }
    }

    public static CompoundTag readNetworkCompressed(byte[] data) throws IOException {
        return readNetworkCompressed(data, ByteOrder.BIG_ENDIAN);
    }

    public static CompoundTag readNetworkCompressed(byte[] data, ByteOrder endianness) throws IOException {
        try (InputStream bytes = new ByteArrayInputStream(data); 
             InputStream gzip = new GZIPInputStream(bytes); 
             InputStream buffered = new BufferedInputStream(gzip)) {
            return read(buffered, endianness, true);
        }
    }

    public static byte[] write(CompoundTag tag) throws IOException {
        return write(tag, ByteOrder.BIG_ENDIAN);
    }

    public static byte[] write(CompoundTag tag, ByteOrder endianness) throws IOException {
        return write(tag, endianness, false);
    }

    public static byte[] write(CompoundTag tag, ByteOrder endianness, boolean network) throws IOException {
        return write((Tag) tag, endianness, network);
    }

    public static byte[] write(Tag tag, ByteOrder endianness, boolean network) throws IOException {
        FastByteArrayOutputStream baos = ThreadCache.fbaos.get().reset();
        try (NBTOutputStream stream = new NBTOutputStream(baos, endianness, network)) {
            Tag.writeNamedTag(tag, stream);
            return baos.toByteArray();
        }
    }

    public static byte[] write(Collection<CompoundTag> tags) throws IOException {
        return write(tags, ByteOrder.BIG_ENDIAN);
    }

    public static byte[] write(Collection<CompoundTag> tags, ByteOrder endianness) throws IOException {
        return write(tags, endianness, false);
    }

    public static byte[] write(Collection<CompoundTag> tags, ByteOrder endianness, boolean network) throws IOException {
        FastByteArrayOutputStream baos = ThreadCache.fbaos.get().reset();
        try (NBTOutputStream stream = new NBTOutputStream(baos, endianness, network)) {
            for (CompoundTag tag : tags) {
                Tag.writeNamedTag(tag, stream);
            }
            return baos.toByteArray();
        }
    }

    public static void write(CompoundTag tag, File file) throws IOException {
        write(tag, file, ByteOrder.BIG_ENDIAN);
    }

    public static void write(CompoundTag tag, File file, ByteOrder endianness) throws IOException {
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            write(tag, outputStream, endianness);
        }
    }

    public static void write(CompoundTag tag, OutputStream outputStream) throws IOException {
        write(tag, outputStream, ByteOrder.BIG_ENDIAN);
    }

    public static void write(CompoundTag tag, OutputStream outputStream, ByteOrder endianness) throws IOException {
        write(tag, outputStream, endianness, false);
    }

    public static void write(CompoundTag tag, OutputStream outputStream, ByteOrder endianness, boolean network) throws IOException {
        Tag.writeNamedTag(tag, new NBTOutputStream(outputStream, endianness, network));
    }

    public static byte[] writeNetwork(Tag tag) throws IOException {
        FastByteArrayOutputStream baos = ThreadCache.fbaos.get().reset();
        try (NBTOutputStream stream = new NBTOutputStream(baos, ByteOrder.LITTLE_ENDIAN, true)) {
            Tag.writeNamedTag(tag, stream);
        }
        return baos.toByteArray();
    }

    public static byte[] writeGZIPCompressed(CompoundTag tag) throws IOException {
        return writeGZIPCompressed(tag, ByteOrder.BIG_ENDIAN);
    }

    public static byte[] writeGZIPCompressed(CompoundTag tag, ByteOrder endianness) throws IOException {
        FastByteArrayOutputStream baos = ThreadCache.fbaos.get().reset();
        writeGZIPCompressed(tag, baos, endianness);
        return baos.toByteArray();
    }

    public static void writeGZIPCompressed(CompoundTag tag, OutputStream outputStream) throws IOException {
        writeGZIPCompressed(tag, outputStream, ByteOrder.BIG_ENDIAN);
    }

    public static void writeGZIPCompressed(CompoundTag tag, OutputStream outputStream, ByteOrder endianness) throws IOException {
        PGZIPOutputStream gzip = new PGZIPOutputStream(outputStream);
        write(tag, gzip, endianness);
        gzip.finish();
    }

    public static byte[] writeNetworkGZIPCompressed(CompoundTag tag) throws IOException {
        return writeNetworkGZIPCompressed(tag, ByteOrder.BIG_ENDIAN);
    }

    public static byte[] writeNetworkGZIPCompressed(CompoundTag tag, ByteOrder endianness) throws IOException {
        FastByteArrayOutputStream baos = ThreadCache.fbaos.get().reset();
        writeNetworkGZIPCompressed(tag, baos, endianness);
        return baos.toByteArray();
    }

    public static void writeNetworkGZIPCompressed(CompoundTag tag, OutputStream outputStream) throws IOException {
        writeNetworkGZIPCompressed(tag, outputStream, ByteOrder.BIG_ENDIAN);
    }

    public static void writeNetworkGZIPCompressed(CompoundTag tag, OutputStream outputStream, ByteOrder endianness) throws IOException {
        PGZIPOutputStream gzip = new PGZIPOutputStream(outputStream);
        write(tag, gzip, endianness, true);
        gzip.finish();
    }

    public static void writeZLIBCompressed(CompoundTag tag, OutputStream outputStream) throws IOException {
        writeZLIBCompressed(tag, outputStream, ByteOrder.BIG_ENDIAN);
    }

    public static void writeZLIBCompressed(CompoundTag tag, OutputStream outputStream, ByteOrder endianness) throws IOException {
        writeZLIBCompressed(tag, outputStream, Deflater.DEFAULT_COMPRESSION, endianness);
    }

    public static void writeZLIBCompressed(CompoundTag tag, OutputStream outputStream, int level) throws IOException {
        writeZLIBCompressed(tag, outputStream, level, ByteOrder.BIG_ENDIAN);
    }

    public static void writeZLIBCompressed(CompoundTag tag, OutputStream outputStream, int level, ByteOrder endianness) throws IOException {
        DeflaterOutputStream out = new DeflaterOutputStream(outputStream, new Deflater(level));
        write(tag, out, endianness);
        out.finish();
    }

    public static void safeWrite(CompoundTag tag, File file) throws IOException {
        File tmpFile = new File(file.getAbsolutePath() + "_tmp");
        if (tmpFile.exists()) {
            tmpFile.delete();
        }
        write(tag, tmpFile);
        Files.move(tmpFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * The following methods
     * only used for LevelEventGenericPacket
     * which do not write/read tag id and name
     */

    @PowerNukkitXOnly
    @Since("1.19.21-r3")
    public static byte[] writeValue(CompoundTag tag) throws IOException {
        return writeValue(tag, ByteOrder.BIG_ENDIAN);
    }

    @PowerNukkitXOnly
    @Since("1.19.21-r3")
    public static byte[] writeValue(CompoundTag tag, ByteOrder endianness) throws IOException {
        return writeValue(tag, endianness, false);
    }

    @PowerNukkitXOnly
    @Since("1.19.21-r3")
    public static byte[] writeValue(CompoundTag tag, ByteOrder endianness, boolean network) throws IOException {
        return writeValue((Tag) tag, endianness, network);
    }

    @PowerNukkitXOnly
    @Since("1.19.21-r3")
    public static byte[] writeValue(Tag tag, ByteOrder endianness, boolean network) throws IOException {
        FastByteArrayOutputStream baos = ThreadCache.fbaos.get().reset();
        try (NBTOutputStream stream = new NBTOutputStream(baos, endianness, network)) {
            Tag.writeValue(tag, stream);
            return baos.toByteArray();
        }
    }

    @PowerNukkitXOnly
    @Since("1.19.21-r3")
    public static CompoundTag readCompoundValue(InputStream inputStream, ByteOrder endianness, boolean network) throws IOException {
        return Tag.readCompoundValue(new NBTInputStream(inputStream, endianness, network));
    }
}
