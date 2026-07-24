package com.nkolay.ucmd.cacheloader.config;

import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertySourceFactory;

import java.io.IOException;
import java.util.List;

/**
 * @PropertySource'un yml dosyalarını da okuyabilmesi için basit factory.
 * Sadece cacheloader modülü kullanır; core konfigürasyona dokunmaz.
 */
public class YamlPropertySourceFactory implements PropertySourceFactory {

    @Override
    public PropertySource<?> createPropertySource(String name, EncodedResource resource) throws IOException {
        String sourceName = (name != null) ? name : resource.getResource().getFilename();
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load(sourceName, resource.getResource());
        if (sources.isEmpty()) {
            throw new IOException("Boş veya okunamayan yml: " + sourceName);
        }
        // Tek dokümanlı dosya bekliyoruz; ilkini dön.
        return sources.get(0);
    }
}
