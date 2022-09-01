package com.venafi.vcert.sdk.connectors.tpp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.security.Security;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import com.sshtools.common.publickey.SshKeyPairGenerator;
import com.sshtools.common.publickey.SshKeyUtils;
import com.sshtools.common.ssh.components.SshKeyPair;
import com.venafi.vcert.sdk.TestUtils;
import com.venafi.vcert.sdk.VCertException;
import com.venafi.vcert.sdk.certificate.SshCaTemplateRequest;
import com.venafi.vcert.sdk.certificate.SshCertRetrieveDetails;
import com.venafi.vcert.sdk.certificate.SshCertificateRequest;
import com.venafi.vcert.sdk.certificate.SshConfig;
import com.venafi.vcert.sdk.endpoint.Authentication;
import com.venafi.vcert.sdk.utils.TppTestUtils;

class TppConnectorATForSSH {

    private TppConnector classUnderTest = new TppConnector(Tpp.connect(TestUtils.TPP_URL));

    @BeforeEach
    void authenticate(TestInfo testInfo) throws VCertException {
    	if(testInfo.getTags()!=null && !testInfo.getTags().contains("AuthenticationUnneeded")) {
    		Security.addProvider(new BouncyCastleProvider());
    		Authentication authentication = new Authentication()
    				.user(TestUtils.TPP_USER)
    				.password(TestUtils.TPP_PASSWORD)
    				.scope("ssh:manage");
    		classUnderTest.authenticate(authentication);
    	}
    }

	@Test
	@DisplayName("TPP - Testing the requestSshCertificate() and retrieveSshCertificate() methods when KeyPair is provided")
	public void requestAndRetrieveSshCertificateWithKeyPairProvided() throws VCertException, Exception {

		String keyId = TppTestUtils.getRandSshKeyId();
		
		//getting an SSH Key Pair with a key size of 3072 bits
		SshKeyPair pair = SshKeyPairGenerator.generateKeyPair(SshKeyPairGenerator.SSH2_RSA, 3072);

		//extracting the Public Key and adding the KeyId as comment, at the end of the Public Key
		//because TPP returns the Public Key on that way
		String publicKeyData = SshKeyUtils.getFormattedKey(pair.getPublicKey(), keyId);

		//building an SshCertificateRequest
		SshCertificateRequest req = new SshCertificateRequest()
				.keyId( keyId )
				.validityPeriod("4h")
				.template(System.getenv("TPP_SSH_CA"))
				.publicKeyData(publicKeyData)
				.sourceAddresses(new String[]{"test.com"});
		
		//requesting the SSH Certificate
		String pickUpID = classUnderTest.requestSshCertificate(req);

		//setting the pickUp ID
		req.pickupID(pickUpID);

		//retrieving the Cert and details
		SshCertRetrieveDetails sshCertRetrieveDetails  = classUnderTest.retrieveSshCertificate(req);
		
		assertEquals(publicKeyData, sshCertRetrieveDetails.publicKeyData());
		assertNotNull(sshCertRetrieveDetails.certificateData());
		
		Long validityPeriodFromCert = Long.parseLong(sshCertRetrieveDetails.certificateDetails().validTo()) - Long.parseLong(sshCertRetrieveDetails.certificateDetails().validFrom());
		
		assertEquals(14400L, validityPeriodFromCert.longValue());//4h
	}

	@Test
	@DisplayName("TPP - Testing the requestSshCertificate() and retrieveSshCertificate() methods when the KeyPair is not provided and it will be generated by the Server")
	public void requestAndRetrieveSshCertificate() throws VCertException, Exception {

		SshCertificateRequest req = new SshCertificateRequest()
				.keyId(TppTestUtils.getRandSshKeyId())
				.validityPeriod("4h")
				.template(System.getenv("TPP_SSH_CA"))
				.sourceAddresses(new String[]{"test.com"});

		//requesting the SSH Certificate
		String pickUpID = classUnderTest.requestSshCertificate(req);
		
		//setting the pickUp ID
		req.pickupID(pickUpID);
		//setting a passphrase to the KeyPair service generated
		req.privateKeyPassphrase("my-passphrase");

		//retrieving the Cert and details
		SshCertRetrieveDetails sshCertRetrieveDetails  = classUnderTest.retrieveSshCertificate(req);

		assertNotNull(sshCertRetrieveDetails.certificateData());
		
		//The following it should works correctly given that the passphrase is correct.
		SshKeyPair sshKeyPair = SshKeyUtils.getPrivateKey(sshCertRetrieveDetails.privateKeyData(), "my-passphrase");
		
		assertNotNull(sshKeyPair);
	}
	
	@Test
	@DisplayName("TPP - Testing the retrieveSshConfig() method using CA name")
	public void retrieveSshConfigFromCAName() throws VCertException, Exception {
		
		SshCaTemplateRequest req = new SshCaTemplateRequest()
				.template(System.getenv("TPP_SSH_CA"));

		//getting the sshConfig of the SSH Cert CA
		retrieveSshConfig(req);
	}
	
	@Test
	@DisplayName("TPP - Testing the retrieveSshConfig() method using CADN")
	public void retrieveSshConfigFromCADN() throws VCertException, Exception {
		
		SshCaTemplateRequest req = new SshCaTemplateRequest()
				.template(System.getenv("TPP_SSH_CADN"));

		//getting the sshConfig of the SSH Cert CA
		retrieveSshConfig(req);
	}
	
	private void retrieveSshConfig(SshCaTemplateRequest req) throws VCertException, Exception {
		
		//getting the sshConfig of the SSH Cert CA
		SshConfig sshConfig = classUnderTest.retrieveSshConfig(req);

		assertNotNull(sshConfig);
		assertNotNull(sshConfig.caPublicKey());
		assertTrue(!sshConfig.caPublicKey().isEmpty());
		assertNotNull(sshConfig.principals());
		assertTrue(sshConfig.principals().length>0);
	}
	
	@Test
	@Tag("AuthenticationUnneeded")
	@DisplayName("TPP - Testing the retrieveSshConfig() method without authentication using CA name")
	public void retrieveSshConfigWithoutCredentialsFromCAName() throws VCertException, Exception {

		//Given this test is tagged as AuthenticationUnneeded, then the Authentication will not be performed
		SshCaTemplateRequest req = new SshCaTemplateRequest()
				.template(System.getenv("TPP_SSH_CA"));

		//getting the sshConfig of the SSH Cert CA
		retrieveSshConfigWithoutCredentials(req);
	}
	
	@Test
	@Tag("AuthenticationUnneeded")
	@DisplayName("TPP - Testing the retrieveSshConfig() method without authentication using CADN")
	public void retrieveSshConfigWithoutCredentialsFromCADN() throws VCertException, Exception {

		//Given this test is tagged as AuthenticationUnneeded, then the Authentication will not be performed
		SshCaTemplateRequest req = new SshCaTemplateRequest()
				.template(System.getenv("TPP_SSH_CADN"));

		//getting the sshConfig of the SSH Cert CA
		retrieveSshConfigWithoutCredentials(req);
	}
	
	private void retrieveSshConfigWithoutCredentials(SshCaTemplateRequest req) throws VCertException, Exception {

		//getting the sshConfig of the SSH Cert CA
		SshConfig sshConfig = classUnderTest.retrieveSshConfig(req);

		assertNotNull(sshConfig);
		assertNotNull(sshConfig.caPublicKey());
		assertTrue(!sshConfig.caPublicKey().isEmpty());
		//When the authentication is not provided, then the principals are not retrieved
		assertNull(sshConfig.principals());
	}
}
