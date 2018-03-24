package github.joeyslalom.bootstrap;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.impl.conn.DefaultSchemePortResolver;
import org.apache.http.impl.conn.SystemDefaultRoutePlanner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.vault.support.ClientOptions;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.ProxySelector;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

import static org.springframework.vault.config.AbstractVaultConfiguration.ClientFactoryWrapper;

@Configuration
public class VaultBootstrapConfig {



    /**
     * From VaultBootstrapConfiguration.  Create a ClientHttpRequestFactory that uses a cert for TLS with Vault.
     */
    @Bean
    public ClientFactoryWrapper clientHttpRequestFactoryWrapper(@Value("ca-cert.pem") ClassPathResource cert) {

        SSLContext sslContext;
        try {
            sslContext = getSslContext(cert);
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException("Error while setting up Vault SSL", e);
        }

        return new ClientFactoryWrapper(usingHttpComponents(sslContext));
    }

    private SSLContext getSslContext(Resource cert) throws GeneralSecurityException, IOException {
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        Certificate caCert = certFactory.generateCertificate(cert.getInputStream());
        TrustManagerFactory trustManagerFactory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null);
        ks.setCertificateEntry("caCert", caCert);
        trustManagerFactory.init(ks);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();

        if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager)) {
            throw new IllegalStateException("Unexpected default trust managers");
        }

        sslContext.init(null, trustManagers, null);
        return sslContext;
    }


    // From ClientHttpRequestFactoryFactory.HttpComponents.usingHttpComponents
    private ClientHttpRequestFactory usingHttpComponents(SSLContext sslContext) {
        ClientOptions options = new ClientOptions();
        HttpClientBuilder httpClientBuilder = HttpClients.custom();

        httpClientBuilder.setRoutePlanner(new SystemDefaultRoutePlanner(
                DefaultSchemePortResolver.INSTANCE, ProxySelector.getDefault()));

        SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext);
        httpClientBuilder.setSSLSocketFactory(sslSocketFactory);
        httpClientBuilder.setSSLContext(sslContext);

        RequestConfig requestConfig = RequestConfig
                .custom()
                .setConnectTimeout(
                        Math.toIntExact(options.getConnectionTimeout().toMillis()))
                .setSocketTimeout(
                        Math.toIntExact(options.getReadTimeout().toMillis()))
                .setAuthenticationEnabled(true)
                .build();

        httpClientBuilder.setDefaultRequestConfig(requestConfig);

        // Support redirects
        httpClientBuilder.setRedirectStrategy(new LaxRedirectStrategy());

        return new HttpComponentsClientHttpRequestFactory(httpClientBuilder.build());
    }
}
