package com.example.shop.service;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "binary.export")
public class BinaryExportProperties {

    @NotBlank
    private String manifestMagic;

    @NotBlank
    private String dataMagic;

    @NotBlank
    private String manifestFilename;

    @NotBlank
    private String dataFilename;

    public String getManifestMagic() {
        return manifestMagic;
    }

    public void setManifestMagic(String manifestMagic) {
        this.manifestMagic = manifestMagic;
    }

    public String getDataMagic() {
        return dataMagic;
    }

    public void setDataMagic(String dataMagic) {
        this.dataMagic = dataMagic;
    }

    public String getManifestFilename() {
        return manifestFilename;
    }

    public void setManifestFilename(String manifestFilename) {
        this.manifestFilename = manifestFilename;
    }

    public String getDataFilename() {
        return dataFilename;
    }

    public void setDataFilename(String dataFilename) {
        this.dataFilename = dataFilename;
    }
}
