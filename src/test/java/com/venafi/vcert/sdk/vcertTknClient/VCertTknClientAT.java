package com.venafi.vcert.sdk.vcertTknClient;

import static com.venafi.vcert.sdk.TestUtils.getTestIps;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.StringReader;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;

import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.venafi.vcert.sdk.TestUtils;
import com.venafi.vcert.sdk.VCertException;
import com.venafi.vcert.sdk.certificate.CertificateRequest;
import com.venafi.vcert.sdk.certificate.KeyType;
import com.venafi.vcert.sdk.connectors.ZoneConfiguration;

import feign.FeignException;

class VCertTknClientAT {
	
	@RegisterExtension
	public static final VCertTknClientResource clientResource = new VCertTknClientResource();

	@Test
	void readZoneConfiguration() throws VCertException {
		try {
			clientResource.client().readZoneConfiguration(TestUtils.TPP_ZONE);
		} catch (FeignException fe) {
			throw VCertException.fromFeignException(fe);
		}
	}

	@Test
	void readZoneConfigurationInLongFormat() throws VCertException {
		try {
			clientResource.client().readZoneConfiguration("\\VED\\Policy\\"+TestUtils.TPP_ZONE);
		} catch (FeignException fe) {
			throw VCertException.fromFeignException(fe);
		}
	}

	@Test
	void ping() throws VCertException {
		assertThatCode(() -> clientResource.client().ping()).doesNotThrowAnyException();
	}

	@Test
	void generateRequest() throws VCertException, IOException {
		String commonName = TestUtils.randomCN();
		ZoneConfiguration zoneConfiguration = clientResource.client().readZoneConfiguration(TestUtils.TPP_ZONE);
		CertificateRequest certificateRequest = new CertificateRequest()
				.subject(new CertificateRequest.PKIXName().commonName(commonName)
						.organization(Collections.singletonList("Venafi, Inc."))
						.organizationalUnit(Arrays.asList("Engineering", "Automated Tests"))
						.country(Collections.singletonList("US")).locality(Collections.singletonList("SLC"))
						.province(Collections.singletonList("Utah")))
				.dnsNames(Collections.singletonList(InetAddress.getLocalHost().getHostName()))
				.ipAddresses(getTestIps()).keyType(KeyType.RSA).keyLength(2048);

		certificateRequest = clientResource.client().generateRequest(zoneConfiguration, certificateRequest);

		assertThat(certificateRequest.csr()).isNotEmpty();

		PKCS10CertificationRequest request = (PKCS10CertificationRequest) new PEMParser(
				new StringReader(new String(certificateRequest.csr()))).readObject();

		// Values overridden by policy which is why they don't match the above values
		String subject = request.getSubject().toString();

		assertThat(subject).contains(format("CN=%s", commonName));
	}

	@Test
	void readPolicyConfiguration() {
		assertThrows(UnsupportedOperationException.class,
				() -> clientResource.client().readPolicyConfiguration("zone"));
	}
}
