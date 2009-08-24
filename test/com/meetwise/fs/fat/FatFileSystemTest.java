
package com.meetwise.fs.fat;

import com.meetwise.fs.fat.FatFileSystem;
import com.meetwise.fs.fat.BootSector;
import com.meetwise.fs.fat.LfnEntry;
import com.meetwise.fs.fat.FatLfnDirectory;
import com.meetwise.fs.fat.RootDirectoryFullException;
import com.meetwise.fs.fat.FatUtils;
import com.meetwise.fs.fat.FatFormatter;
import com.meetwise.fs.fat.FatType;
import com.meetwise.fs.fat.FatRootEntry;
import com.meetwise.fs.fat.FatDirectory;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Iterator;
import com.meetwise.fs.FSDirectory;
import com.meetwise.fs.FSEntry;
import com.meetwise.fs.FSFile;
import com.meetwise.fs.RamDisk;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Matthias Treydte &lt;waldheinz at gmail.com&gt;
 */
public class FatFileSystemTest {

    @Test
    public void testMaxRootEntries() throws Exception {
        System.out.println("testMaxRootEntries");

        RamDisk d = new RamDisk(512 * 1024);
        final FatFormatter ff = FatFormatter.superFloppyFormatter(d);
        ff.format(d, null);
        final FatFileSystem fs = new FatFileSystem(d, false);
        final FatLfnDirectory root = fs.getRootDir();
        
        /* divide by 2 because we use LFNs which take entries, too */
        final int max = fs.getBootSector().getNrRootDirEntries() / 2;
        
        for (int i=0; i < max; i++) {
            root.addFile("f-" + i);
        }
        
        try {
            root.addFile("fails");
            fail("added too many files to root directory");
        } catch (RootDirectoryFullException ex) {
            /* fine */
        }
    }
    
    /**
     * $ cat fat16-test.img.gz | gunzip | hexdump -C
     *
     * @throws Exception
     */
    @Test
    public void testFat16Read() throws Exception {
        System.out.println("testFat16Read");

        final InputStream is = getClass().getResourceAsStream(
                "/data/fat16-test.img.gz");
        
        final RamDisk rd = RamDisk.readGzipped(is);
        final FatFileSystem fatFs = new FatFileSystem(rd, false);
        assertEquals(2048, fatFs.getClusterSize());
        
        final BootSector bs = fatFs.getBootSector();
        assertEquals("mkdosfs", bs.getOemName());
        assertEquals(512, bs.getBytesPerSector());
        assertEquals(FatType.FAT16, bs.getFatType());
        assertEquals(4, bs.getSectorsPerCluster());
        assertEquals(1, bs.getNrReservedSectors());
        assertEquals(2, bs.getNrFats());
        assertEquals(512, bs.getNrRootDirEntries());
        assertEquals(20000, bs.getSectorCount());
        assertEquals(0xf8, bs.getMediumDescriptor());
        assertEquals(20, bs.getSectorsPerFat());
        assertEquals(32, bs.getSectorsPerTrack());
        assertEquals(64, bs.getNrHeads());
        assertEquals(0, bs.getNrHiddenSectors());
        assertEquals(0x200, FatUtils.getFatOffset(bs, 0));
        assertEquals(0x2a00, FatUtils.getFatOffset(bs, 1));
        assertEquals(0x5200, FatUtils.getRootDirOffset(bs));
        
        final FatDirectory fatRootDir = fatFs.getRootDir();
        assertEquals(512, fatRootDir.getSize());

        FSEntry entry = fatRootDir.getEntry("testFile");
        assertTrue(entry.isFile());
        assertFalse(entry.isDirectory());

        FSFile file = entry.getFile();
        assertEquals(8, file.getLength());
        
        final FatRootEntry rootEnt = fatFs.getRootEntry();
        assertTrue(rootEnt.isDirectory());
        assertNull(rootEnt.getParent());

        final FSDirectory rootDir = rootEnt.getDirectory();
        System.out.println("   rootDir = " + rootDir);

        Iterator<FSEntry> i = rootDir.iterator();
        assertTrue (i.hasNext());
        
        while (i.hasNext()) {
            final FSEntry e = i.next();
            System.out.println("     - " + e);
        }

        entry = rootDir.getEntry("TESTDIR");
        System.out.println("   testEnt = " + entry);
        assertTrue(entry.isDirectory());
        assertFalse(entry.isFile());

        final FSDirectory testDir = entry.getDirectory();
        System.out.println("   testDir = " + testDir);
        
        i = testDir.iterator();
        
        while (i.hasNext()) {
            final FSEntry e = i.next();
            System.out.println("     - " + e);
        }
        
    }

    @Test
    public void testFat32Read() throws Exception {
        System.out.println("testFat32Read");
        
        final InputStream is = getClass().getResourceAsStream(
                "/data/fat32-test.img.gz");

        final RamDisk rd = RamDisk.readGzipped(is);
        final FatFileSystem fatFs = new FatFileSystem(rd, false);
        assertEquals(512, fatFs.getClusterSize());

        final BootSector bs = fatFs.getBootSector();
        assertEquals(FatType.FAT32, bs.getFatType());
        assertEquals("mkdosfs", bs.getOemName());
        assertEquals(512, bs.getBytesPerSector());
        assertEquals(1, bs.getSectorsPerCluster());
        assertEquals(32, bs.getNrReservedSectors());
        assertEquals(2, bs.getNrFats());
        assertEquals(0, bs.getNrRootDirEntries());
        assertEquals(200000, bs.getSectorCount());
        assertEquals(0xf8, bs.getMediumDescriptor());
        assertEquals(1539, bs.getSectorsPerFat());
        assertEquals(32, bs.getSectorsPerTrack());
        assertEquals(64, bs.getNrHeads());
        assertEquals(0, bs.getNrHiddenSectors());
        assertEquals(16384, FatUtils.getFatOffset(bs, 0));
        assertEquals(16384 + 1539 * bs.getBytesPerSector(),
                FatUtils.getFatOffset(bs, 1));
        
        final FatLfnDirectory rootDir = fatFs.getRootDir();
        System.out.println("   rootDir = " + rootDir);
        assertTrue(rootDir.isRoot());
        
        Iterator<FSEntry> i = rootDir.iterator();
        assertTrue(i.hasNext());
        
        while (i.hasNext()) {
            final FSEntry e = i.next();
            System.out.println("     - " + e);
        }

        FSEntry e = rootDir.getEntry("TestDir");
        assertTrue(e.isDirectory());
        assertFalse(e.isFile());

        final FSDirectory dir = e.getDirectory();
        i = dir.iterator();
        assertTrue(i.hasNext());
        
        while (i.hasNext()) {
            e = i.next();
            System.out.println("     - " + e);
        }
    }
    
    @Test
    public void testFat32Write() throws Exception {
        System.out.println("testFat32Write");

        final InputStream is = getClass().getResourceAsStream(
                "/data/fat32-test.img.gz");

        final RamDisk rd = RamDisk.readGzipped(is);
        FatFileSystem fatFs = new FatFileSystem(rd, false);
        FatLfnDirectory rootDir = fatFs.getRootDir();

        for (int i=0; i < 1024; i++) {
            final LfnEntry e = rootDir.addFile("f-" + i);
            assertTrue(e.isFile());
            assertFalse(e.isDirectory());
            final FSFile f = e.getFile();
            
            f.write(0, ByteBuffer.wrap(("this is file # " + i).getBytes()));
        }

        fatFs.close();

        fatFs = new FatFileSystem(rd, false);
        rootDir = fatFs.getRootDir();
        
        for (int i=0; i < 1024; i++) {
            assertNotNull(rootDir.getEntry("f-" + i));
        }
    }

    public static void main(String[] args) throws Exception {
        FatFileSystemTest test = new FatFileSystemTest();
        test.testFat32Write();
    }
}
