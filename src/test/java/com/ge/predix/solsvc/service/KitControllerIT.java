/*
 * Copyright (c) 2017 General Electric Company. All rights reserved.
 *
 * The copyright to the computer software herein is the property of
 * General Electric Company. The software may be used and/or copied only
 * with the written permission of General Electric Company or in accordance
 * with the terms and conditions stipulated in the agreement/contract
 * under which the software has been supplied.
 */

package com.ge.predix.solsvc.service;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.ParseException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.joda.time.DateTime;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.security.oauth2.client.token.grant.client.ClientCredentialsResourceDetails;
import org.springframework.security.oauth2.client.token.grant.password.ResourceOwnerPasswordResourceDetails;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ge.predix.solsvc.ext.util.JsonMapper;
import com.ge.predix.solsvc.kitservice.model.RegisterDevice;
import com.ge.predix.solsvc.restclient.config.IOauthRestConfig;
import com.ge.predix.solsvc.restclient.impl.RestClient;

/**
 * 
 * @author 212421693 -
 */
public class KitControllerIT extends AbstractBaseControllerIT
{

    private static final Logger log = LoggerFactory.getLogger(KitControllerIT.class);

    @Value("${local.server.port}")
    private int                 localServerPort;

    private URL                 base;
    private RestTemplate        template;

    @Autowired
    private RestClient          restClient;

    @Autowired
    @Qualifier("defaultOauthRestConfig")
    private IOauthRestConfig    restConfig;

    @Autowired
    private JsonMapper          jsonMapper;
    private ObjectMapper        objectMapper;

    /**
     * @throws Exception -
     */
    @Before
    public void setUp()
            throws Exception
    {
        this.template = new TestRestTemplate();
        this.template.getMessageConverters().add(new FormHttpMessageConverter());
        this.template.getMessageConverters().add(new MappingJackson2HttpMessageConverter());
        this.objectMapper = new ObjectMapper();
    }

    /**
     * @throws Exception -
     */
    @SuppressWarnings("nls")
    @Test
    public void registerDeviceWithAuthenticationError()
            throws Exception
    {
        this.base = new URL("http://localhost:" + this.localServerPort + "/device/register");
        ResponseEntity<String> response = this.template.getForEntity(this.base.toString(), String.class);
        assertThat(response.getBody(), containsString("unauthorized"));
    }

    /**
     * @throws Exception -
     */
    @SuppressWarnings("nls")
    @Test
    public void registerDevice()
            throws Exception
    {

        String url = "http://localhost:" + this.localServerPort + "/device/register";
        RegisterDevice device = getRegisterDevice();

        String req = this.jsonMapper.toJson(device);
        log.debug("Register Device Json req is " + this.jsonMapper.toJson(device));
        List<Header> headers = new ArrayList<Header>();
        String userToken = getUserToken("app_user_1", "app_user_1");
        headers.add(new BasicHeader("Authorization", userToken));
        headers.add(new BasicHeader("Content-Type", "application/json"));

        CloseableHttpResponse response = null;
        try
        {
            response = this.restClient.post(url, req, headers, this.restConfig.getDefaultConnectionTimeout(),
                    this.restConfig.getDefaultSocketTimeout());
            Assert.assertNotNull(response);
            Assert.assertTrue(response.toString().contains("HTTP/1.1 200 OK"));
            String body = EntityUtils.toString(response.getEntity());
            assertThat(body, containsString("uri"));
            RegisterDevice registeredDevice = this.objectMapper.readValue(body, RegisterDevice.class);
            assertTrue(registeredDevice.getUri() != null);

        }
        finally
        {
            if ( response != null ) response.close();
        }
    }

    /**
     * @throws Exception -
     */
    @SuppressWarnings("nls")
    @Test
    public void updateDevice()
            throws Exception
    {
        List<Header> headers = new ArrayList<Header>();
        String userToken = getUserToken("app_user_1", "app_user_1");
        headers.add(new BasicHeader("Authorization", userToken));
        headers.add(new BasicHeader("Content-Type", "application/json"));
        
        RegisterDevice device = getRegisterDevice();
        String url = "http://localhost:" + this.localServerPort + "/device/" + device.getDeviceAddress();  //$NON-NLS-1$//$NON-NLS-2$

        RegisterDevice updateDevice = getRegisterDevicebyId(headers,url);
        
        updateDevice.setDeviceName("UpdateDevice-Test");

        String req = this.jsonMapper.toJson(updateDevice);
        log.debug("update Device Json req is " + this.jsonMapper.toJson(updateDevice));

        CloseableHttpResponse response = null;
        try
        {
            response = this.restClient.put(url, req, headers, this.restConfig.getDefaultConnectionTimeout(),
                    this.restConfig.getDefaultSocketTimeout());
            Assert.assertNotNull(response);
            Assert.assertTrue(response.toString().contains("HTTP/1.1 200 OK"));
           
            RegisterDevice newUpdatedDevice = getRegisterDevicebyId(headers,url);
            Assert.assertTrue(newUpdatedDevice.getDeviceName().equalsIgnoreCase(updateDevice.getDeviceName()));
        }
        finally
        {
            if ( response != null ) response.close();
        }
    }

    /**
     * @return -
     */
    private RegisterDevice getRegisterDevicebyId(List<Header> headers , String url )
    {
        RegisterDevice registeredDevice = null;
        CloseableHttpResponse response = null;
        try
        {
            response = this.restClient.get(url, headers);
            Assert.assertNotNull(response);
            Assert.assertTrue(response.toString().contains("HTTP/1.1 200 OK"));//$NON-NLS-1$
            String body = EntityUtils.toString(response.getEntity());
            registeredDevice = this.objectMapper.readValue(body, RegisterDevice.class);
            assertTrue(registeredDevice.getUri() != null);

        }
        catch (ParseException | IOException e)
        {
            e.printStackTrace();
        }
        finally
        {
            if ( response != null ) try
            {
                response.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        return registeredDevice;
    }

    /**
     * @throws Exception -
     */
    @SuppressWarnings("nls")
    @Test
    public void registerDeviceValidationFailure()
            throws Exception
    {

        String url = "http://localhost:" + this.localServerPort + "/device/register";
        RegisterDevice device = getRegisterDevice();
       // device.setUserId("");
        //device.setDeviceAddress("");
        device.setDeviceName("!!!!");
        String req = this.jsonMapper.toJson(device);
        log.debug("Register Device Json req is " + this.jsonMapper.toJson(device));
        List<Header> headers = new ArrayList<Header>();
        String userToken = getUserToken("app_user_1", "app_user_1");
        headers.add(new BasicHeader("Authorization", userToken));
        headers.add(new BasicHeader("Content-Type", "application/json"));

        CloseableHttpResponse response = null;
        try
        {
            response = this.restClient.post(url, req, headers, this.restConfig.getDefaultConnectionTimeout(),
                    this.restConfig.getDefaultSocketTimeout());
            Assert.assertNotNull(response);
            Assert.assertTrue(response.toString().contains("HTTP/1.1 400"));
            String body = EntityUtils.toString(response.getEntity());
            assertThat(body, containsString("error"));

        }
        finally
        {
            if ( response != null ) response.close();
        }
    }

    /**
     * 
     * @throws Exception -
     */
    @SuppressWarnings("nls")
    @Test
    public void getDevice()
            throws Exception
    {
        RegisterDevice device = getRegisterDevice();
        String url = "http://localhost:" + this.localServerPort + "/device/" + device.getDeviceAddress();
        List<Header> headers = new ArrayList<Header>();
        String userToken = getUserToken("app_user_1", "app_user_1");
        headers.add(new BasicHeader("Authorization", userToken));
        headers.add(new BasicHeader("Content-Type", "application/json"));

        CloseableHttpResponse response = null;
        try
        {
            response = this.restClient.get(url, headers);
            Assert.assertNotNull(response);
            Assert.assertTrue(response.toString().contains("HTTP/1.1 200 OK"));
            String body = EntityUtils.toString(response.getEntity());
            // assertThat(body, containsString("uri"));
            RegisterDevice registeredDevice = this.objectMapper.readValue(body, RegisterDevice.class);
            assertTrue(registeredDevice.getUri() != null);

        }
        finally
        {
            if ( response != null ) response.close();
        }
    }
    
    /**
     * 
     * @throws Exception -
     */
    @SuppressWarnings("nls")
    @Test
    public void getDeviceValidation()
            throws Exception
    {
        String url = "http://localhost:" + this.localServerPort + "/device/tinfoil_0481%29";
        List<Header> headers = new ArrayList<Header>();
        String userToken = getUserToken("app_user_1", "app_user_1");
        headers.add(new BasicHeader("Authorization", userToken));
        headers.add(new BasicHeader("Content-Type", "application/json"));

        CloseableHttpResponse response = null;
        try
        {
            response = this.restClient.get(url, headers);
            Assert.assertNotNull(response);
            Assert.assertTrue(response.toString().contains("HTTP/1.1 400"));
            String body = EntityUtils.toString(response.getEntity());
            assertThat(body, containsString("error"));

        }
        finally
        {
            if ( response != null ) response.close();
        }
    }

    /**
     * 
     * @throws Exception -
     */
    @SuppressWarnings("nls")
    @Test
    public void getAllDevice()
            throws Exception
    {
        String url = "http://localhost:" + this.localServerPort + "/device/";
        List<Header> headers = new ArrayList<Header>();
        String userToken = getUserToken("app_user_1", "app_user_1");
        headers.add(new BasicHeader("Authorization", userToken));
        headers.add(new BasicHeader("Content-Type", "application/json"));

        CloseableHttpResponse response = null;
        try
        {
            response = this.restClient.get(url, headers);
            Assert.assertNotNull(response);
            Assert.assertTrue(response.toString().contains("HTTP/1.1 200 OK"));
            String body = EntityUtils.toString(response.getEntity());
            List<RegisterDevice> registeredDevices = this.objectMapper.readValue(body, ArrayList.class);
            assertTrue(registeredDevices != null);
            assertTrue(registeredDevices.size() > 1);

        }
        finally
        {
            if ( response != null ) response.close();
        }
    }
    
    /**
     * 
     * @throws Exception -
     */
    @SuppressWarnings({
            "nls", "resource"
    })
    @Test
    public void checkDeviceActivationExpiry()
            throws Exception
    {

        String url = "http://localhost:" + this.localServerPort + "/device/register";
        RegisterDevice device = getRegisterDevice();
        device.setUri("/device/testexpiry");
        device.setGroupRef("/group/testexpiry");
        device.setDeviceName("TEST-EXPIRY");
        device.setDeviceAddress("TEST-EXPIRY");
        DateTime lastWeek = new DateTime().minusDays(60+1);
        device.setActivationDate(String.valueOf(lastWeek.getMillis()));
        String req = this.jsonMapper.toJson(device);
        log.debug("Register Device Json req is " + this.jsonMapper.toJson(device));
        List<Header> headers = new ArrayList<Header>();
        String userToken = getUserToken("app_user_1", "app_user_1");
        System.out.println("user token" +userToken);
        headers.add(new BasicHeader("Authorization", userToken));
        headers.add(new BasicHeader("Content-Type", "application/json"));

        CloseableHttpResponse response = null;
        try
        {
            response = this.restClient.post(url, req, headers, this.restConfig.getDefaultConnectionTimeout(),
                    this.restConfig.getDefaultSocketTimeout());
            Assert.assertNotNull(response);
            System.out.println("in here "+response.toString());
            // first time response is 200OK, next time its a validation failure due to expire time check
            Assert.assertTrue(response.toString().contains("HTTP/1.1 200 OK") || (response.toString().contains("400 Bad Request") )); 
            String geturl = "http://localhost:" + this.localServerPort + "/device/"+device.getDeviceAddress();
            response = this.restClient.get(geturl, headers);
            String body = EntityUtils.toString(response.getEntity());
            assertThat(body, containsString("Device has past its activation period."));
      
        }
        finally
        {
            if ( response != null ) response.close();
        }
    }

    /**
     * @return -
     */
    private RegisterDevice getRegisterDevice()
    {
        RegisterDevice device = new RegisterDevice();
        // device.setUri("/device/test-guid"); //$NON-NLS-1$
        device.setActivationDate(String.valueOf(Instant.now().toEpochMilli()));
        // device.setCreatedDate(String.valueOf(Instant.now().toEpochMilli()));
        device.setDeviceName("NUC-124-test-2"); //$NON-NLS-1$
        device.setDeviceAddress("NUC-124-test-2"); //$NON-NLS-1$
        device.setDeviceType("NUC"); //$NON-NLS-1$
        device.setGroupRef("/group/testcompany-2"); //$NON-NLS-1$
        // device.setUserId("bd9f70a3-8aaa-490b-b2a8-91ba59e58f0f"); //$NON-NLS-1$
        device.setUri("/device/" + device.getDeviceAddress()); //$NON-NLS-1$
        return device;
    }

    /**
     * Returns a OAuth2RestTemplate based on the username password
     */

    private String getUserToken(String username, String password)
    {
        // get token here based on username password;
        ResourceOwnerPasswordResourceDetails resourceDetails = new ResourceOwnerPasswordResourceDetails();
        resourceDetails.setUsername(username);
        resourceDetails.setPassword(password);

        String url = this.restConfig.getOauthIssuerId();

        resourceDetails.setAccessTokenUri(url);

        String[] clientIds = this.restConfig.getOauthClientId().split(":");
        resourceDetails.setClientId(clientIds[0]);
        resourceDetails.setClientSecret(clientIds[1]);

        OAuth2RestTemplate restTemplate = new OAuth2RestTemplate(resourceDetails);
        OAuth2AccessToken token = restTemplate.getAccessToken();

        return token.getTokenType() + " " + token.getValue();
    }
    
    
    private String getClientToken(String clientId, String secret)
    {
        ClientCredentialsResourceDetails clientCreds = new ClientCredentialsResourceDetails();
        String url = this.restConfig.getOauthIssuerId();
        clientCreds.setAccessTokenUri(url);
        clientCreds.setClientId(clientId);
        clientCreds.setClientSecret(secret);
        OAuth2RestTemplate restTemplate = new OAuth2RestTemplate(clientCreds);
        OAuth2AccessToken token = restTemplate.getAccessToken();
        return token.getTokenType() + " " + token.getValue();
    }
    
    
    
    @SuppressWarnings("nls")
    @Test
    public void getMachineDevice()
            throws Exception
    {
        RegisterDevice device = getRegisterDevice();
        String url = "http://localhost:" + this.localServerPort + "/device/" + device.getDeviceAddress();
        List<Header> headers = new ArrayList<Header>();
        String clientToken = getClientToken("device_client_id", "secret");
        headers.add(new BasicHeader("Authorization", clientToken));
        headers.add(new BasicHeader("Content-Type", "application/json"));

        CloseableHttpResponse response = null;
        try
        {
            response = this.restClient.get(url, headers);
            Assert.assertNotNull(response);
            Assert.assertTrue(response.toString().contains("HTTP/1.1 200 OK"));
            String body = EntityUtils.toString(response.getEntity());
            // assertThat(body, containsString("uri"));
            RegisterDevice registeredDevice = this.objectMapper.readValue(body, RegisterDevice.class);
            assertTrue(registeredDevice.getUri() != null);

        }
        finally
        {
            if ( response != null ) response.close();
        }
    }

}
