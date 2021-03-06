package com.polopoly.ps.fileserver.resources;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

import org.junit.Test;
import org.xml.sax.SAXException;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebResponse;
import com.polopoly.ps.fileserver.repository.RepositoryStorageException;
import com.polopoly.ps.fileserver.resources.FileResourceResource;
import com.polopoly.ps.fileserver.util.DeleteMethodWebRequest;


public class FileResourceResourceIntegrationTest extends AbstractResourceIntegrationTest {
    private static final String CLASS = FileResourceResourceIntegrationTest.class.getName();
    private static final Logger LOG = Logger.getLogger(CLASS);
    private static final String URL = WEBAPP + FileResourceResource.RESOURCE + "/";

    private final long fixtureImageCRC = computeCRC32(new File(FIXTURE_IMAGE));
    public static final Pattern URL_ID_EXTRACTOR = Pattern.compile(".*/(\\d+)/filename/.*");

    private long computeCRC32(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data);
        return crc.getValue();
    }

    private long computeCRC32(File file) {
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(file);
            byte[] data = new byte[inputStream.available()];
            inputStream.read(data, 0, inputStream.available());
            return computeCRC32(data);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close(inputStream);
        }
        return 0;
    }

    private void close(InputStream inputStream) {
        try {
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private WebResponse uploadFile(String path) throws IOException, SAXException {
        File originalFile = new File(path);
        PostMethodWebRequest req = new PostMethodWebRequest(URL + TEST_IMAGE, new FileInputStream(originalFile), "image/jpeg");
        WebResponse resp = wc.getResponse(req);
        return resp;
    }

    private WebResponse getFile(String path) throws IOException, SAXException {
        GetMethodWebRequest req = new GetMethodWebRequest(path);
        WebResponse resp = wc.getResponse(req);
        return resp;
    }

    private WebResponse deleteFile(String path) throws IOException, SAXException {
        DeleteMethodWebRequest req = new DeleteMethodWebRequest(path);
        WebResponse resp = wc.getResponse(req);
        return resp;
    }

    @Test
    public void postImage() throws IOException, SAXException, RepositoryStorageException {
        WebResponse resp = uploadFile(FIXTURE_IMAGE);
        assertNotNull("Response was null", resp);
        assertEquals(201, resp.getResponseCode());
        String url = resp.getHeaderField("Location");
        LOG.log(Level.INFO, "Got url " + url);

        assertTrue("wrong URL: " + url, url.matches(URL + "\\d+" + "/filename/" + TEST_IMAGE));
        // Why is this easier than comparing file contents?
        assertEquals("CRCs do not match", this.fixtureImageCRC, computeCRC32(repository.getFileResource(parseIdfromURL(url)).getData()));
    }

    private int getFileIndex(String url) {
        return Integer.valueOf(url.split("/")[url.split("/").length - 3]);
    }

    @Test
    public void deleteImage() throws Exception {
        WebResponse resp;
        resp = uploadFile(FIXTURE_IMAGE);
        String url = resp.getHeaderField("Location");
        resp = deleteFile(url);
        resp = getFile(url);
        assertEquals(resp.getResponseCode(), 404);
        int index = getFileIndex(url);
        resp = uploadFile(FIXTURE_IMAGE);
        url = resp.getHeaderField("Location");

        assertEquals("Expecting newIndex to be directly following index.", getFileIndex(url), index + 1);
    }

    @Test
    public void postBigImage() throws RepositoryStorageException, IOException, SAXException {
        File originalFile = new File(BIG_FIXTURE_IMAGE);
        PostMethodWebRequest req = new PostMethodWebRequest(URL + BIG_TEST_IMAGE, new FileInputStream(originalFile), "image/jpeg");
        WebResponse resp = wc.getResponse(req);
        assertNotNull("Response was null", resp);
        assertEquals(201, resp.getResponseCode());
        String url = resp.getHeaderField("Location");

        assertTrue("wrong URL: " + url, url.matches(URL + "\\d+" + "/filename/" + BIG_TEST_IMAGE));

        // Why is this easier than comparing file contents?
        assertEquals(computeCRC32(originalFile), computeCRC32(repository.getFileResource(parseIdfromURL(url)).getData()));

    }

    @Test
    public void getImage() throws IOException, SAXException {
        copyTestImageToRepository();
        GetMethodWebRequest req = new GetMethodWebRequest(URL + "0");
        WebResponse resp = wc.getResponse(req);

        assertNotNull("Response was null", resp);
        assertEquals(200, resp.getResponseCode());
        int contentLength = resp.getContentLength();
        byte[] data = new byte[contentLength];
        resp.getInputStream().read(data);
        assertEquals(fixtureImageCRC, computeCRC32(data));
    }

    @Test
    public void wrongExtensionImage() throws IOException, SAXException {
        File originalFile = new File(WRONG_EXTENSION_FIXTURE_IMAGE);
        PostMethodWebRequest postReq = new PostMethodWebRequest(URL + WRONG_EXTENSION_TEST_IMAGE, new FileInputStream(originalFile), "image/gif");
        WebResponse resp = wc.getResponse(postReq);
        assertNotNull("Response was null", resp);
        assertEquals(201, resp.getResponseCode());
        String url = resp.getHeaderField("Location");

        GetMethodWebRequest getReq = new GetMethodWebRequest(url);
        resp = wc.getResponse(getReq);

        assertNotNull("Response was null", resp);
        assertEquals(200, resp.getResponseCode());
        assertEquals("image/jpeg", resp.getContentType());

    }

    private String parseIdfromURL(String url) {
        Matcher matcher = URL_ID_EXTRACTOR.matcher(url);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        throw new RuntimeException("cannot extract ID from url: " + url);
    }
}