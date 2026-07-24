package ph.edu.dlsu.lbycpob.profilemanagerapplication.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Iterator;
import java.util.UUID;

/**
 * Compresses uploaded avatars to WebP and pushes them to a Supabase
 * Storage bucket, returning the resulting public URL.
 * <p>
 * Requires these three properties to be set (see application.properties):
 * supabase.url, supabase.bucket, supabase.service-role-key
 */
@Service
public class PictureStorageService {

    private final String supabaseUrl;
    private final String bucket;
    private final String serviceRoleKey;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public PictureStorageService(
            @Value("${supabase.url:}") String supabaseUrl,
            @Value("${supabase.bucket:avatars}") String bucket,
            @Value("${supabase.service-role-key:}") String serviceRoleKey) {
        // Trim any trailing slash so URL-building below never double-slashes.
        this.supabaseUrl = supabaseUrl.endsWith("/") ? supabaseUrl.substring(0, supabaseUrl.length() - 1) : supabaseUrl;
        this.bucket = bucket;
        this.serviceRoleKey = serviceRoleKey;
    }

    public String storeAvatar(UUID profileId, MultipartFile file) {
        if (supabaseUrl.isBlank() || serviceRoleKey.isBlank()) {
            throw new IllegalStateException(
                    "Supabase storage is not configured. Set supabase.url and supabase.service-role-key " +
                            "in application.properties before uploading avatars.");
        }

        byte[] webpBytes = toWebp(file);
        String objectPath = "avatars/" + profileId + ".webp";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(supabaseUrl + "/storage/v1/object/" + bucket + "/" + objectPath))
                    .header("Authorization", "Bearer " + serviceRoleKey)
                    .header("apikey", serviceRoleKey)
                    .header("Content-Type", "image/webp")
                    .header("x-upsert", "true")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(webpBytes))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("Supabase upload failed (" + response.statusCode() + "): " + response.body());
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Could not reach Supabase Storage: " + e.getMessage(), e);
        }

        return supabaseUrl + "/storage/v1/object/public/" + bucket + "/" + objectPath;
    }

    private byte[] toWebp(MultipartFile file) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(file.getBytes()));
            if (image == null) {
                throw new IllegalArgumentException("The uploaded file is not a readable image.");
            }

            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("webp");
            if (!writers.hasNext()) {
                throw new IllegalStateException("No WebP writer available (webp-imageio dependency missing?).");
            }
            ImageWriter writer = writers.next();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(out)) {
                writer.setOutput(ios);
                ImageWriteParam params = writer.getDefaultWriteParam();
                if (params.canWriteCompressed()) {
                    params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    params.setCompressionQuality(0.8f);
                }
                writer.write(null, new IIOImage(image, null, null), params);
            } finally {
                writer.dispose();
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not process the uploaded image: " + e.getMessage(), e);
        }
    }
}
