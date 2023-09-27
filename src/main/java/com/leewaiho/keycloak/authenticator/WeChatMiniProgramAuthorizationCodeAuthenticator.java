package com.leewaiho.keycloak.authenticator;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.authenticators.directgrant.AbstractDirectGrantAuthenticator;
import org.keycloak.broker.provider.util.SimpleHttp;
import org.keycloak.events.Errors;
import org.keycloak.models.*;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author liwh01@mingyuanyun.com
 * @since 2023/4/26 20:46
 **/
public class WeChatMiniProgramAuthorizationCodeAuthenticator extends AbstractDirectGrantAuthenticator {

    public static final String PROVIDER_ID = "direct-grant-wmp-authorization-code";

    private static final String CODE2SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session";

    private static final ObjectMapper mapper = new ObjectMapper();

    private final Logger log = LoggerFactory.getLogger(WeChatMiniProgramAuthorizationCodeAuthenticator.class);

    protected static final AuthenticationExecutionModel.Requirement[] REQUIREMENT_CHOICES = {
        AuthenticationExecutionModel.Requirement.REQUIRED,
    };

    protected static final List<ProviderConfigProperty> configProperties = new ArrayList<>();

    static {
        ProviderConfigProperty property;
        property = new ProviderConfigProperty();
        property.setName("id");
        property.setLabel("AppId");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        configProperties.add(property);

        property = new ProviderConfigProperty();
        property.setName("secret");
        property.setLabel("AppSecret");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setSecret(true);
        configProperties.add(property);
    }

    @Override
    public void authenticate(AuthenticationFlowContext ctx) {
        AuthenticatorConfigModel configModel = ctx.getAuthenticatorConfig();
        if (configModel == null) {
            ctx.getEvent().error(Errors.INVALID_CONFIG);
            Response challengeResponse = errorResponse(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), "服务配置异常", "微信小程序登录配置异常");
            ctx.failure(AuthenticationFlowError.INTERNAL_ERROR, challengeResponse);
            return;
        }
        log.info("登录配置: {}({})", configModel.getAlias(), configModel.getId());
        Map<String, String> config = configModel.getConfig();
        String appId = config.get("id");
        String appSecret = config.get("secret");

        KeycloakSession keycloakSession = ctx.getSession();
        String code = ctx.getHttpRequest().getDecodedFormParameters().getFirst("code");
        log.info("开始进行身份验证, appId: {} code: {}", appId, code);

        String sessionUrl = CODE2SESSION_URL
            + "?appid=" + appId
            + "&secret=" + appSecret
            + "&js_code=" + code
            + "&grant_type=authorization_code";
        JsonNode responseJSON;
        try (SimpleHttp.Response response = SimpleHttp.doGet(sessionUrl, keycloakSession).asResponse()) {
            String responseText = response.asString();
            log.info("接收到响应: {}", responseText);
            responseJSON = mapper.readTree(responseText);
        } catch (IOException e) {
            throw new IllegalStateException("获取微信小程序用户身份异常", e);
        }
        String errcode = getJsonProperty(responseJSON, "errcode");
        log.info("errcode: {}", errcode);
        if (StringUtil.isNotBlank(errcode)) {
            ctx.getEvent().error("获取微信小程序用户身份异常: " + errcode);
            Response challengeResponse = errorResponse(Response.Status.UNAUTHORIZED.getStatusCode(), "获取微信小程序用户身份异常", StrUtil.format("errcode: {} errmsg: {}", errcode, getJsonProperty(responseJSON, "errmsg")));
            ctx.failure(AuthenticationFlowError.INVALID_USER, challengeResponse);
            return;
        }

        String openid = getJsonProperty(responseJSON, "openid");
        log.info("用户openid: {}", openid);
        Optional<UserModel> selectedUser = ctx.getSession().users().searchForUserByUserAttributeStream(ctx.getRealm(), getOpenIDAttributeName(appId), openid)
            .findFirst();
        if (selectedUser.isPresent()) {
            log.info("用户已存在, openid: {}", openid);
            ctx.setUser(selectedUser.get());
            ctx.success();
        } else {
            log.info("用户不存在, openid: {}", openid);
            String unionid = getJsonProperty(responseJSON, "unionid");
            log.info("用户unionid: {}", unionid);
            UserModel user = ctx.getSession().users().addUser(ctx.getRealm(), openid);
            user.setEnabled(true);
            user.setSingleAttribute(getOpenIDAttributeName(appId), openid);
            user.setSingleAttribute(getUnionIDAttributeName(appId), unionid);
            ctx.setUser(user);
            ctx.success();
        }
    }

    private static String getOpenIDAttributeName(String appId) {
        return "wmp_" + appId + "_openid";
    }

    private static String getUnionIDAttributeName(String appId) {
        return "wmp_" + appId + "_unionid";
    }

    private static String getJsonProperty(JsonNode jsonNode, String key) {
        if (!jsonNode.has(key) || jsonNode.get(key).isNull()) {
            return null;
        }
        return jsonNode.get(key).asText();
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {

    }

    @Override
    public String getDisplayType() {
        return "微信小程序授权码登录";
    }

    @Override
    public String getReferenceCategory() {
        return null;
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return REQUIREMENT_CHOICES;
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public String getHelpText() {
        return "用户可以使用微信小程序获取的authorization_code进行登录, 当SSO用户不存在时，自动根据填入信息创建";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return configProperties;
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}
