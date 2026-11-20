package kr.budget.web;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class PortalAssets {
    private record Asset(String hash, MediaType type, byte[] content) {}
    private final Map<String,Asset> assets = new HashMap<>();
    private final Map<String,String> paths = new HashMap<>();

    public PortalAssets() throws IOException, NoSuchAlgorithmException {
        for (String name : new String[]{"portal.css","portal.js","policy-editor.js"}) {
            var resource = new ClassPathResource("static/assets/" + name);
            if (!resource.exists() && name.equals("policy-editor.js")) continue;
            byte[] content = resource.getContentAsByteArray();
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
            MediaType type = MediaType.parseMediaType(name.endsWith(".css") ? "text/css;charset=UTF-8" : "text/javascript;charset=UTF-8");
            assets.put(name,new Asset(hash,type,content));
            paths.put(name,"/assets/" + hash + "/" + name);
        }
    }

    @GetMapping("/")
    public String home(CsrfToken token, Model model) {
        token.getToken();
        model.addAttribute("assetPaths",paths);
        return "portal";
    }

    @GetMapping("/assets/{hash}/{name}")
    public ResponseEntity<byte[]> asset(@PathVariable String hash, @PathVariable String name) {
        Asset asset = assets.get(name);
        if (asset == null || !asset.hash().equals(hash)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(asset.type())
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(asset.content());
    }
}
