/*************************************************************************
 *                                                                       *
 *  EJBCA Community: The OpenSource Certificate Authority                *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/
package org.ejbca.core.model.era;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.ejb.EJB;
import javax.ejb.RemoveException;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceException;
import javax.persistence.Query;
import javax.persistence.QueryTimeoutException;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.cesecore.CesecoreException;
import org.cesecore.ErrorCode;
import org.cesecore.authentication.AuthenticationFailedException;
import org.cesecore.authentication.tokens.AlwaysAllowLocalAuthenticationToken;
import org.cesecore.authentication.tokens.AuthenticationToken;
import org.cesecore.authentication.tokens.PublicAccessAuthenticationTokenMetaData;
import org.cesecore.authentication.tokens.UsernamePrincipal;
import org.cesecore.authorization.AuthorizationDeniedException;
import org.cesecore.authorization.AuthorizationSessionLocal;
import org.cesecore.authorization.access.AccessSet;
import org.cesecore.authorization.cache.AccessTreeUpdateSessionLocal;
import org.cesecore.authorization.control.AuditLogRules;
import org.cesecore.authorization.control.StandardRules;
import org.cesecore.authorization.user.matchvalues.AccessMatchValue;
import org.cesecore.authorization.user.matchvalues.AccessMatchValueReverseLookupRegistry;
import org.cesecore.certificates.ca.ApprovalRequestType;
import org.cesecore.certificates.ca.CAConstants;
import org.cesecore.certificates.ca.CADoesntExistsException;
import org.cesecore.certificates.ca.CAInfo;
import org.cesecore.certificates.ca.CAOfflineException;
import org.cesecore.certificates.ca.CaSessionLocal;
import org.cesecore.certificates.ca.IllegalNameException;
import org.cesecore.certificates.ca.IllegalValidityException;
import org.cesecore.certificates.ca.InvalidAlgorithmException;
import org.cesecore.certificates.ca.SignRequestException;
import org.cesecore.certificates.ca.SignRequestSignatureException;
import org.cesecore.certificates.certificate.CertificateConstants;
import org.cesecore.certificates.certificate.CertificateCreateException;
import org.cesecore.certificates.certificate.CertificateCreateSessionLocal;
import org.cesecore.certificates.certificate.CertificateDataWrapper;
import org.cesecore.certificates.certificate.CertificateRevokeException;
import org.cesecore.certificates.certificate.CertificateStoreSessionLocal;
import org.cesecore.certificates.certificate.CertificateWrapper;
import org.cesecore.certificates.certificate.IllegalKeyException;
import org.cesecore.certificates.certificate.certextensions.CertificateExtensionException;
import org.cesecore.certificates.certificate.exception.CertificateSerialNumberException;
import org.cesecore.certificates.certificate.exception.CustomCertificateSerialNumberException;
import org.cesecore.certificates.certificate.request.PKCS10RequestMessage;
import org.cesecore.certificates.certificate.request.RequestMessageUtils;
import org.cesecore.certificates.certificate.request.ResponseMessage;
import org.cesecore.certificates.certificate.request.X509ResponseMessage;
import org.cesecore.certificates.certificateprofile.CertificateProfile;
import org.cesecore.certificates.certificateprofile.CertificateProfileConstants;
import org.cesecore.certificates.certificateprofile.CertificateProfileSessionLocal;
import org.cesecore.certificates.endentity.EndEntityConstants;
import org.cesecore.certificates.endentity.EndEntityInformation;
import org.cesecore.certificates.endentity.ExtendedInformation;
import org.cesecore.config.CesecoreConfiguration;
import org.cesecore.config.GlobalCesecoreConfiguration;
import org.cesecore.config.RaStyleInfo;
import org.cesecore.configuration.GlobalConfigurationSessionLocal;
import org.cesecore.keys.token.CryptoTokenOfflineException;
import org.cesecore.keys.token.CryptoTokenSessionLocal;
import org.cesecore.keys.util.KeyTools;
import org.cesecore.roles.Role;
import org.cesecore.roles.RoleExistsException;
import org.cesecore.roles.management.RoleDataSessionLocal;
import org.cesecore.roles.management.RoleSessionLocal;
import org.cesecore.roles.member.RoleMember;
import org.cesecore.roles.member.RoleMemberData;
import org.cesecore.roles.member.RoleMemberSessionLocal;
import org.cesecore.util.CertTools;
import org.cesecore.util.EJBTools;
import org.cesecore.util.StringTools;
import org.cesecore.util.ValidityDate;
import org.ejbca.config.GlobalConfiguration;
import org.ejbca.config.GlobalCustomCssConfiguration;
import org.ejbca.core.EjbcaException;
import org.ejbca.core.ejb.EnterpriseEditionEjbBridgeSessionLocal;
import org.ejbca.core.ejb.approval.ApprovalExecutionSessionLocal;
import org.ejbca.core.ejb.approval.ApprovalProfileSessionLocal;
import org.ejbca.core.ejb.approval.ApprovalSessionLocal;
import org.ejbca.core.ejb.authentication.cli.CliAuthenticationTokenMetaData;
import org.ejbca.core.ejb.authorization.AuthorizationSystemSessionLocal;
import org.ejbca.core.ejb.ca.auth.EndEntityAuthenticationSessionLocal;
import org.ejbca.core.ejb.ca.sign.SignSessionLocal;
import org.ejbca.core.ejb.hardtoken.HardTokenSessionLocal;
import org.ejbca.core.ejb.keyrecovery.KeyRecoverySessionLocal;
import org.ejbca.core.ejb.ra.CertificateRequestSessionLocal;
import org.ejbca.core.ejb.ra.EndEntityAccessSessionLocal;
import org.ejbca.core.ejb.ra.EndEntityManagementSessionLocal;
import org.ejbca.core.ejb.ra.KeyStoreCreateSessionLocal;
import org.ejbca.core.ejb.ra.NoSuchEndEntityException;
import org.ejbca.core.ejb.ra.raadmin.EndEntityProfileSessionLocal;
import org.ejbca.core.ejb.ra.userdatasource.UserDataSourceSessionLocal;
import org.ejbca.core.ejb.ws.EjbcaWSHelperSessionLocal;
import org.ejbca.core.model.CertificateSignatureException;
import org.ejbca.core.model.InternalEjbcaResources;
import org.ejbca.core.model.SecConst;
import org.ejbca.core.model.approval.AdminAlreadyApprovedRequestException;
import org.ejbca.core.model.approval.Approval;
import org.ejbca.core.model.approval.ApprovalDataText;
import org.ejbca.core.model.approval.ApprovalDataVO;
import org.ejbca.core.model.approval.ApprovalException;
import org.ejbca.core.model.approval.ApprovalRequest;
import org.ejbca.core.model.approval.ApprovalRequestExecutionException;
import org.ejbca.core.model.approval.ApprovalRequestExpiredException;
import org.ejbca.core.model.approval.SelfApprovalException;
import org.ejbca.core.model.approval.WaitingForApprovalException;
import org.ejbca.core.model.approval.approvalrequests.AddEndEntityApprovalRequest;
import org.ejbca.core.model.approval.approvalrequests.EditEndEntityApprovalRequest;
import org.ejbca.core.model.approval.profile.ApprovalProfile;
import org.ejbca.core.model.authorization.AccessRulesConstants;
import org.ejbca.core.model.ca.AuthLoginException;
import org.ejbca.core.model.ca.AuthStatusException;
import org.ejbca.core.model.ra.AlreadyRevokedException;
import org.ejbca.core.model.ra.CustomFieldException;
import org.ejbca.core.model.ra.EndEntityProfileValidationRaException;
import org.ejbca.core.model.ra.KeyStoreGeneralRaException;
import org.ejbca.core.model.ra.NotFoundException;
import org.ejbca.core.model.ra.RAAuthorization;
import org.ejbca.core.model.ra.raadmin.EndEntityProfile;
import org.ejbca.core.model.ra.raadmin.EndEntityProfileValidationException;
import org.ejbca.core.protocol.cmp.CmpMessageDispatcherSessionLocal;
import org.ejbca.core.protocol.cmp.NoSuchAliasException;
import org.ejbca.core.protocol.est.EstOperationsSessionLocal;
import org.ejbca.core.protocol.ws.common.CertificateHelper;
import org.ejbca.core.protocol.ws.objects.UserDataVOWS;
import org.ejbca.ui.web.protocol.CertificateRenewalException;
import org.ejbca.util.query.ApprovalMatch;
import org.ejbca.util.query.BasicMatch;
import org.ejbca.util.query.IllegalQueryException;

/**
 * Implementation of the RaMasterApi that invokes functions at the local node.
 *
 * @version $Id$
 */
@Stateless//(mappedName = JndiConstants.APP_JNDI_PREFIX + "RaMasterApiSessionRemote")
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class RaMasterApiSessionBean implements RaMasterApiSessionLocal {

    private static final Logger log = Logger.getLogger(RaMasterApiSessionBean.class);
    private static final InternalEjbcaResources intres = InternalEjbcaResources.getInstance();

    @EJB
    private AccessTreeUpdateSessionLocal accessTreeUpdateSession;
    @EJB
    private ApprovalProfileSessionLocal approvalProfileSession;
    @EJB
    private ApprovalSessionLocal approvalSession;
    @EJB
    private ApprovalExecutionSessionLocal approvalExecutionSession;
    @EJB
    private AuthorizationSessionLocal authorizationSession;
    @EJB
    private AuthorizationSystemSessionLocal authorizationSystemSession;
    @EJB
    private CaSessionLocal caSession;
    @EJB
    private CertificateProfileSessionLocal certificateProfileSession;
    @EJB
    private CertificateRequestSessionLocal certificateRequestSession;
    @EJB
    private CertificateStoreSessionLocal certificateStoreSession;
    @EJB
    private CertificateCreateSessionLocal certificateCreateSession;
    @EJB
    private CmpMessageDispatcherSessionLocal cmpMessageDispatcherSession;
    @EJB
    private CryptoTokenSessionLocal cryptoTokenSession;
    @EJB
    private EnterpriseEditionEjbBridgeSessionLocal enterpriseEditionEjbBridgeSession;
    @EJB
    private EjbcaWSHelperSessionLocal ejbcaWSHelperSession;
    @EJB
    private EndEntityAccessSessionLocal endEntityAccessSession;
    @EJB
    private EndEntityManagementSessionLocal endEntityManagementSession;
    @EJB
    private EndEntityProfileSessionLocal endEntityProfileSession;
    @EJB
    private EndEntityManagementSessionLocal endEntityManagementSessionLocal;
    @EJB
    private EstOperationsSessionLocal estOperationsSessionLocal;
    @EJB
    private GlobalConfigurationSessionLocal globalConfigurationSession;
    @EJB
    private HardTokenSessionLocal hardTokenSession;
    @EJB
    private KeyRecoverySessionLocal keyRecoverySessionLocal;
    @EJB
    private KeyStoreCreateSessionLocal keyStoreCreateSessionLocal;
    @EJB
    private UserDataSourceSessionLocal userDataSourceSession;
    @EJB
    private SignSessionLocal signSessionLocal;
    @EJB
    private EndEntityAuthenticationSessionLocal endEntityAuthenticationSessionLocal;
    @EJB
    private RoleSessionLocal roleSession;
    @EJB
    private RoleDataSessionLocal roleDataSession;
    @EJB
    private RoleMemberSessionLocal roleMemberSession;

    @PersistenceContext(unitName = CesecoreConfiguration.PERSISTENCE_UNIT)
    private EntityManager entityManager;

    private static final int RA_MASTER_API_VERSION = 2;

    /** Cached value of an active CA, so we don't have to list through all CAs every time as this is a critical path executed every time */
    private int activeCaIdCache = -1;

    @Override
    public boolean isBackendAvailable() {
        try {
            if (activeCaIdCache != -1) {
                if (caSession.getCAInfoInternal(activeCaIdCache).getStatus() == CAConstants.CA_ACTIVE) {
                    return true;
                }
            }
        } catch (CADoesntExistsException e) {
            activeCaIdCache = -1;
            log.debug("Fail to get cached CA's info. " + e.getMessage());
        }
        // If the cached activeCaIdCache was not active, or didn't exist, we move on to check all in the list
        for (int caId : caSession.getAllCaIds()) {
            try {
                if (caSession.getCAInfoInternal(caId).getStatus() == CAConstants.CA_ACTIVE) {
                    activeCaIdCache = caId; // Remember this value for the next time
                    return true;
                }
            } catch (CADoesntExistsException e) {
                log.debug("Fail to get existing CA's info. " + e.getMessage());
            }
        }
        return false;
    }

    @Override
    public int getApiVersion() {
        return RA_MASTER_API_VERSION;
    }

    @Override
    public boolean isAuthorizedNoLogging(AuthenticationToken authenticationToken, String... resources) {
        return authorizationSession.isAuthorizedNoLogging(authenticationToken, resources);
    }

    @Override
    public RaAuthorizationResult getAuthorization(AuthenticationToken authenticationToken) throws AuthenticationFailedException {
        final HashMap<String, Boolean> accessRules = authorizationSession.getAccessAvailableToAuthenticationToken(authenticationToken);
        final int updateNumber = accessTreeUpdateSession.getAccessTreeUpdateNumber();
        return new RaAuthorizationResult(accessRules, updateNumber);
    }

    @Override
    @Deprecated
    public AccessSet getUserAccessSet(final AuthenticationToken authenticationToken) throws AuthenticationFailedException  {
        return authorizationSystemSession.getAccessSetForAuthToken(authenticationToken);
    }

    @Override
    @Deprecated
    public List<AccessSet> getUserAccessSets(final List<AuthenticationToken> authenticationTokens)  {
        final List<AccessSet> ret = new ArrayList<>();
        for (final AuthenticationToken authenticationToken : authenticationTokens) {
            try {
                ret.add(authorizationSystemSession.getAccessSetForAuthToken(authenticationToken));
            } catch (AuthenticationFailedException e) {
                // Always add, even if null. Otherwise the caller won't be able to determine which AccessSet belongs to which AuthenticationToken
                ret.add(null);
            }
        }
        return ret;
    }

    @Override
    public List<CAInfo> getAuthorizedCas(AuthenticationToken authenticationToken) {
        return caSession.getAuthorizedCaInfos(authenticationToken);
    }

    private LinkedHashMap<Integer, RaStyleInfo> getAllCustomRaCss() {
        GlobalCustomCssConfiguration globalCustomCssConfiguration = (GlobalCustomCssConfiguration) globalConfigurationSession.getCachedConfiguration(GlobalCustomCssConfiguration.CSS_CONFIGURATION_ID);
        // Returns an empty map if no CSS was found
        return globalCustomCssConfiguration.getRaStyleInfo();
    }

    @Override
    public LinkedHashMap<Integer,RaStyleInfo> getAllCustomRaStyles(AuthenticationToken authenticationToken) throws AuthorizationDeniedException {
        boolean authorizedToCssArchives = isAuthorizedNoLogging(authenticationToken,
                StandardRules.SYSTEMCONFIGURATION_VIEW.resource(), StandardRules.VIEWROLES.resource());
        if (!authorizedToCssArchives) {
            throw new AuthorizationDeniedException(authenticationToken + " is not authorized to CSS archives");
        }
        return getAllCustomRaCss();
    }

    @Override
    public List<RaStyleInfo> getAvailableCustomRaStyles(AuthenticationToken authenticationToken, int hashCodeOfCurrentList) {
        List<RaStyleInfo> associatedCss = new ArrayList<>();
        LinkedHashMap<Integer, RaStyleInfo> allCssInfos = getAllCustomRaCss();
        List<Role> isMemberOf = roleSession.getRolesAuthenticationTokenIsMemberOf(authenticationToken);
        for (Role role : isMemberOf) {
            RaStyleInfo cssToAdd = allCssInfos.get(role.getStyleId());
            if (cssToAdd != null) {
                associatedCss.add(allCssInfos.get(role.getStyleId()));
            }
        }
        if (associatedCss.hashCode() == hashCodeOfCurrentList) {
            return null;
        } else {
            return associatedCss;

        }
    }

    @Override
    public List<Role> getAuthorizedRoles(AuthenticationToken authenticationToken) {
        return roleSession.getAuthorizedRoles(authenticationToken);
    }

    @Override
    public Role getRole(final AuthenticationToken authenticationToken, final int roleId) throws AuthorizationDeniedException {
        return roleSession.getRole(authenticationToken, roleId);
    }

    @Override
    public List<String> getAuthorizedRoleNamespaces(final AuthenticationToken authenticationToken, final int roleId) {
        // Skip roles that come from other peers if roleId is set
        try {
            if (roleId != Role.ROLE_ID_UNASSIGNED && getRole(authenticationToken, roleId) == null) {
                if (log.isDebugEnabled()) {
                    log.debug("Requested role with ID " + roleId + " does not exist on this system, returning empty list of namespaces");
                }
                return new ArrayList<>();
            }
        } catch (AuthorizationDeniedException e) {
            // Should usually not happen
            if (log.isDebugEnabled()) {
                log.debug("Client " + authenticationToken + "was denied authorization to role with ID " + roleId + ", returning empty list of namespaces");
            }
            return new ArrayList<>();
        }

        return roleSession.getAuthorizedNamespaces(authenticationToken);
    }

    @Override
    public Map<String,RaRoleMemberTokenTypeInfo> getAvailableRoleMemberTokenTypes(final AuthenticationToken authenticationToken) {
        final Map<String,RaRoleMemberTokenTypeInfo> result = new HashMap<>();
        for (final String tokenType : AccessMatchValueReverseLookupRegistry.INSTANCE.getAllTokenTypes()) {
            // Disallow access to Public Access and CLI token types on the RA, as well as non-user-configurable token types such as AlwaysAllowLocal
            if (!AccessMatchValueReverseLookupRegistry.INSTANCE.getMetaData(tokenType).isUserConfigurable() ||
                    PublicAccessAuthenticationTokenMetaData.TOKEN_TYPE.equals(tokenType) ||
                    CliAuthenticationTokenMetaData.TOKEN_TYPE.equals(tokenType)) {
                continue;
            }

            final Map<String,Integer> stringToNumberMap = new HashMap<>();
            for (final Entry<String,AccessMatchValue> entry : AccessMatchValueReverseLookupRegistry.INSTANCE.getNameLookupRegistryForTokenType(tokenType).entrySet()) {
                stringToNumberMap.put(entry.getKey(), entry.getValue().getNumericValue());
            }
            final AccessMatchValue defaultValue = AccessMatchValueReverseLookupRegistry.INSTANCE.getDefaultValueForTokenType(tokenType);
            final boolean hasMatchTypes = !defaultValue.getAvailableAccessMatchTypes().isEmpty();

            result.put(tokenType, new RaRoleMemberTokenTypeInfo(stringToNumberMap, defaultValue.name(), defaultValue.isIssuedByCa(),
                    hasMatchTypes, hasMatchTypes ? defaultValue.getAvailableAccessMatchTypes().get(0).getNumericValue() : 0));

        }
        return result;
    }

    @Override
    public Role saveRole(final AuthenticationToken authenticationToken, final Role role) throws AuthorizationDeniedException, RoleExistsException {
        if (role.getRoleId() != Role.ROLE_ID_UNASSIGNED) {
            // Updating a role
            Role oldRole = roleSession.getRole(authenticationToken, role.getRoleId());
            if (oldRole == null) {
                if (log.isDebugEnabled()) {
                    log.debug("Role with ID " + role.getRoleId() + " does not exist on this system, and will not be updated here. The role name to save was '" + role.getRoleNameFull() + "'");
                }
                return null; // not present on this system
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("Persisting a role with ID " + role.getRoleId() + " and name '" + role.getRoleNameFull() + "'");
        }
        return roleSession.persistRole(authenticationToken, role);
    }

    @Override
    public boolean deleteRole(AuthenticationToken authenticationToken, int roleId) throws AuthorizationDeniedException {
        if (log.isDebugEnabled()) {
            log.debug("Deleting role with ID " + roleId);
        }
        return roleSession.deleteRoleIdempotent(authenticationToken, roleId);
    }

    @Override
    public RoleMember getRoleMember(final AuthenticationToken authenticationToken, final int roleMemberId) throws AuthorizationDeniedException {
        return roleMemberSession.getRoleMember(authenticationToken, roleMemberId);
    }

    @Override
    public RoleMember saveRoleMember(final AuthenticationToken authenticationToken, final RoleMember roleMember) throws AuthorizationDeniedException {
        if (log.isDebugEnabled()) {
            log.debug("Persisting a role member with ID " + roleMember.getRoleId() + " and match value '" + roleMember.getTokenMatchValue() + "'");
        }
        return roleMemberSession.persist(authenticationToken, roleMember);
    }

    @Override
    public boolean deleteRoleMember(AuthenticationToken authenticationToken, int roleId, int roleMemberId) throws AuthorizationDeniedException {
        if (log.isDebugEnabled()) {
            log.debug("Removing role member with ID " + roleMemberId + " from the role with ID " + roleId);
        }
        final RoleMember roleMember = roleMemberSession.getRoleMember(authenticationToken, roleMemberId);
        if (roleMember == null) {
            if (log.isDebugEnabled()) {
                log.debug("Can't delete role member with ID " + roleMemberId + " because it does not exist.");
            }
            log.info("Client " + authenticationToken + " failed to delete role member with ID " + roleMemberId + " because it does not exist.");
            return false;
        }
        // Sanity check that there's no ID collision
        if (roleMember.getRoleId() != roleId) {
            if (log.isDebugEnabled()) {
                log.debug("Role member has an unexpected Role ID " + roleMemberId + ". Role ID " + roleId);
            }
            return false;
        }

        return roleMemberSession.remove(authenticationToken, roleMemberId);
    }


    private ApprovalDataVO getApprovalDataNoAuth(final int id) {
        final org.ejbca.util.query.Query query = new org.ejbca.util.query.Query(org.ejbca.util.query.Query.TYPE_APPROVALQUERY);
        query.add(ApprovalMatch.MATCH_WITH_UNIQUEID, BasicMatch.MATCH_TYPE_EQUALS, Integer.toString(id));

        final List<ApprovalDataVO> approvals;
        try {
            approvals = approvalSession.query(query, 0, 100, "", ""); // authorization checks are performed afterwards
        } catch (IllegalQueryException e) {
            throw new IllegalStateException("Query for approval request failed: " + e.getMessage(), e);
        }

        if (approvals.isEmpty()) {
            return null;
        }

        return approvals.iterator().next();
    }

    /** @param approvalId Calculated hash of the request (this somewhat confusing name is re-used from the ApprovalRequest class)
     * @return ApprovalDataVO or null if not found
     */
    private ApprovalDataVO getApprovalDataByRequestHash(final int approvalId) {
        final List<ApprovalDataVO> approvalDataVOs = approvalSession.findApprovalDataVO(approvalId);
        return approvalDataVOs.isEmpty() ? null : approvalDataVOs.get(0);
    }

    /** Gets the complete text representation of a request (unlike ApprovalRequest.getNewRequestDataAsText which doesn't do any database queries) */
    private List<ApprovalDataText> getRequestDataAsText(final AuthenticationToken authenticationToken, final ApprovalDataVO approval) {
        final ApprovalRequest approvalRequest = approval.getApprovalRequest();
        if (approvalRequest instanceof EditEndEntityApprovalRequest) {
            return ((EditEndEntityApprovalRequest)approvalRequest).getNewRequestDataAsText(caSession, endEntityProfileSession, certificateProfileSession, hardTokenSession);
        } else if (approvalRequest instanceof AddEndEntityApprovalRequest) {
            return ((AddEndEntityApprovalRequest)approvalRequest).getNewRequestDataAsText(caSession, endEntityProfileSession, certificateProfileSession, hardTokenSession);
        } else {
            return approvalRequest.getNewRequestDataAsText(authenticationToken);
        }
    }

    private RaEditableRequestData getRequestEditableData(final AuthenticationToken authenticationToken, final ApprovalDataVO advo) {
        final ApprovalRequest approvalRequest = advo.getApprovalRequest();
        final RaEditableRequestData editableData = new RaEditableRequestData();
        EndEntityInformation userdata = null;

        if (approvalRequest instanceof EditEndEntityApprovalRequest) {
            final EditEndEntityApprovalRequest req = (EditEndEntityApprovalRequest)approvalRequest;
            userdata = req.getNewEndEntityInformation();
        } else if (approvalRequest instanceof AddEndEntityApprovalRequest) {
            final AddEndEntityApprovalRequest req = (AddEndEntityApprovalRequest)approvalRequest;
            userdata = req.getEndEntityInformation();
        }
        // TODO handle more types or approval requests? (ECA-5290)

        if (userdata != null) {
            editableData.setUsername(userdata.getUsername());
            editableData.setEmail(userdata.getEmail());
            editableData.setSubjectDN(userdata.getDN());
            editableData.setSubjectAltName(userdata.getSubjectAltName());
            if (userdata.getExtendedInformation() != null) {
                final ExtendedInformation ei = userdata.getExtendedInformation();
                editableData.setSubjectDirAttrs(ei.getSubjectDirectoryAttributes());
            }
        }

        return editableData;
    }

    @Override
    public RaApprovalRequestInfo getApprovalRequest(final AuthenticationToken authenticationToken, final int id) {
        final ApprovalDataVO advo = getApprovalDataNoAuth(id);
        if (advo == null) {
            return null;
        }
        return getApprovalRequest(authenticationToken, advo);
    }

    @Override
    public RaApprovalRequestInfo getApprovalRequestByRequestHash(final AuthenticationToken authenticationToken, final int approvalId) {
        final ApprovalDataVO advo = getApprovalDataByRequestHash(approvalId);
        if (advo == null) {
            return null;
        }
        return getApprovalRequest(authenticationToken, advo);
    }

    private RaApprovalRequestInfo getApprovalRequest(final AuthenticationToken authenticationToken, final ApprovalDataVO advo) {
        // By getting the CA we perform an implicit auth check
        String caName;
        if (advo.getCAId() == ApprovalDataVO.ANY_CA) {
            caName = null;
        } else {
            try {
                final CAInfo cainfo = caSession.getCAInfo(authenticationToken, advo.getCAId());
                caName = cainfo.getName();
            } catch (AuthorizationDeniedException e) {
                if (log.isDebugEnabled()) {
                    log.debug("Administrator " + authenticationToken + " was denied access to CA " + advo.getCAId() + ". Returning null instead of the approval with ID " + advo.getId());
                }
                return null;
            } catch (CADoesntExistsException e) {
                if (log.isDebugEnabled()) {
                    log.debug("Appproval request " + advo.getId() + " references CA ID " + advo.getCAId() + " which doesn't exist");
                }
                caName = "Missing CA ID " + advo.getCAId();
            }
        }

        final ApprovalRequest approvalRequest = advo.getApprovalRequest();
        final String endEntityProfileName = endEntityProfileSession.getEndEntityProfileName(advo.getEndEntityProfileId());
        final EndEntityProfile endEntityProfile = endEntityProfileSession.getEndEntityProfile(advo.getEndEntityProfileId());
        final String certificateProfileName;
        if (approvalRequest instanceof AddEndEntityApprovalRequest) {
            certificateProfileName = certificateProfileSession.getCertificateProfileName(((AddEndEntityApprovalRequest)approvalRequest).getEndEntityInformation().getCertificateProfileId());
        } else if (approvalRequest instanceof EditEndEntityApprovalRequest) {
            certificateProfileName = certificateProfileSession.getCertificateProfileName(((EditEndEntityApprovalRequest)approvalRequest).getNewEndEntityInformation().getCertificateProfileId());
        } else {
            certificateProfileName = null;
        }

        // Get request data as text
        final List<ApprovalDataText> requestData = getRequestDataAsText(authenticationToken, advo);

        // Editable data
        final RaEditableRequestData editableData = getRequestEditableData(authenticationToken, advo);

        return new RaApprovalRequestInfo(authenticationToken, caName, endEntityProfileName, endEntityProfile, certificateProfileName, advo,
                requestData, editableData);

    }

    @Override
    public RaApprovalRequestInfo editApprovalRequest(final AuthenticationToken authenticationToken, final RaApprovalEditRequest edit) throws AuthorizationDeniedException {
        final int id = edit.getId();
        if (log.isDebugEnabled()) {
            log.debug("Editing approval request " + id + ". Administrator: " + authenticationToken);
        }
        final ApprovalDataVO advo = getApprovalDataNoAuth(id);
        if (advo == null) {
            if (log.isDebugEnabled()) {
                log.debug("Approval Request with ID " + id + " not found in editApprovalRequest");
            }
            // This method may be called on multiple nodes (e.g. both locally on RA, and on multiple CAs),
            // so we must not throw any exceptions on the nodes where the request does not exist.
            return null;
        } else if (getApprovalRequest(authenticationToken, advo) == null) { // Authorization check
            if (log.isDebugEnabled()) {
                log.debug("Authorization denied to approval request with ID " + id + " for administrator '" + authenticationToken + "'");
            }
            throw new AuthorizationDeniedException(authenticationToken + " is not authorized to the Request with ID " + id + " at this point");
        }

        if (advo.getStatus() != ApprovalDataVO.STATUS_WAITINGFORAPPROVAL) {
            throw new IllegalStateException("Was not in waiting for approval state");
        }

        if (!advo.getApprovals().isEmpty()) {
            throw new IllegalStateException("Can't edit a request that has one or more approvals");
        }

        final ApprovalRequest approvalRequest = advo.getApprovalRequest();
        final RaEditableRequestData editData = edit.getEditableData();

        // Can only edit approvals that we have requested, or that we are authorized to approve (ECA-5408)
        final AuthenticationToken requestAdmin = approvalRequest.getRequestAdmin();
        final boolean requestedByMe = requestAdmin != null && requestAdmin.equals(authenticationToken);
        if (requestedByMe) {
            if (log.isDebugEnabled()) {
                log.debug("Request (ID " + id + ") was created by this administrator, so authorization is granted. Editing administrator: '" + authenticationToken + "'");
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Will perform approval authorization check, because request (ID " + id + ") was create by another administrator '" + requestAdmin + "'. Editing administrator: '" + authenticationToken + "'");
            }
            approvalExecutionSession.assertAuthorizedToApprove(authenticationToken, advo);
        }

        if (approvalRequest instanceof AddEndEntityApprovalRequest) {
            // Quick check for obviously illegal values
            if (StringUtils.isEmpty(editData.getUsername()) || StringUtils.isEmpty(editData.getSubjectDN())) {
                throw new IllegalArgumentException("Attempted to set Username or Subject DN to an empty value");
            }

            final AddEndEntityApprovalRequest addReq = (AddEndEntityApprovalRequest) approvalRequest;
            final EndEntityInformation userdata = addReq.getEndEntityInformation();
            userdata.setUsername(editData.getUsername());
            userdata.setEmail(editData.getEmail());
            userdata.setDN(editData.getSubjectDN());
            userdata.setSubjectAltName(editData.getSubjectAltName());
            if (userdata.getExtendedInformation() == null && editData.getSubjectDirAttrs() != null) {
                userdata.setExtendedInformation(new ExtendedInformation());
            }
            final ExtendedInformation ei = userdata.getExtendedInformation();
            ei.setSubjectDirectoryAttributes(editData.getSubjectDirAttrs());
        } else {
            // TODO implement more types of requests? (ECA-5290)
            if (log.isDebugEnabled()) {
                log.debug("Tried to edit approval request with ID " + id + " which is of an unsupported type: " + approvalRequest.getClass().getName());
            }
            throw new IllegalStateException("Editing of this type of request is not implemented: " + approvalRequest.getClass().getName());
        }

        try {
            approvalSession.editApprovalRequest(authenticationToken, id, approvalRequest);
        } catch (ApprovalException e) {
            // Shouldn't happen
            throw new IllegalStateException(e);
        }

        final int newCalculatedHash = approvalRequest.generateApprovalId();
        final Collection<ApprovalDataVO> advosNew = approvalSession.findApprovalDataVO(newCalculatedHash);
        if (advosNew.isEmpty()) {
            throw new IllegalStateException("Approval with calculated hash (approvalId) " + newCalculatedHash + " could not be found");
        }
        final ApprovalDataVO advoNew = advosNew.iterator().next();
        return getApprovalRequest(authenticationToken, advoNew);
    }

    @Override
    public void extendApprovalRequest(final AuthenticationToken authenticationToken, final int id, final long extendForMillis) throws AuthorizationDeniedException {
        final ApprovalDataVO advo = getApprovalDataNoAuth(id);
        if (advo == null) {
            if (log.isDebugEnabled()) {
                log.debug("Approval request with ID " + id + " does not exist on this node.");
            }
            return;
        }

        if (getApprovalRequest(authenticationToken, advo) == null) { // Check read authorization (includes authorization to referenced CAs)
            if (log.isDebugEnabled()) {
                log.debug("Authorization denied to approval request ID " + id + " for " + authenticationToken);
            }
            throw new AuthorizationDeniedException(authenticationToken + " is not authorized to the Request with ID " + id + " at this point");
        }

        // Check specifically for approval authorization
        approvalExecutionSession.assertAuthorizedToApprove(authenticationToken, advo);

        approvalSession.extendApprovalRequestNoAuth(authenticationToken, id, extendForMillis);
    }

    @Override
    public boolean addRequestResponse(final AuthenticationToken authenticationToken, final RaApprovalResponseRequest requestResponse)
            throws AuthorizationDeniedException, ApprovalException, ApprovalRequestExpiredException, ApprovalRequestExecutionException,
            AdminAlreadyApprovedRequestException, SelfApprovalException, AuthenticationFailedException {
        final ApprovalDataVO advo = getApprovalDataNoAuth(requestResponse.getId());
        if (advo == null) {
            // Return false so the next master api backend can see if it can handle the approval
            return false;
        } else if (getApprovalRequest(authenticationToken, advo) == null) { // Check read authorization (includes authorization to referenced CAs)
            if (log.isDebugEnabled()) {
                log.debug("Authorization denied to approval request ID " + requestResponse.getId() + " for " + authenticationToken);
            }
            throw new AuthorizationDeniedException(authenticationToken + " is not authorized to the Request with ID " + requestResponse.getId() + " at this point");
        }

        // Check specifically for approval authorization
        approvalExecutionSession.assertAuthorizedToApprove(authenticationToken, advo);

        // Save the update request (needed if there are properties, e.g. checkboxes etc. in the partitions)
        approvalSession.updateApprovalRequest(advo.getId(), requestResponse.getApprovalRequest());

        // Add the approval
        final Approval approval = new Approval(requestResponse.getComment(), requestResponse.getStepIdentifier(), requestResponse.getPartitionIdentifier());
        switch (requestResponse.getAction()) {
        case APPROVE:
            approvalExecutionSession.approve(authenticationToken, advo.getApprovalId(), approval);
            return true;
        case REJECT:
            approvalExecutionSession.reject(authenticationToken, advo.getApprovalId(), approval);
            return true;
        case SAVE:
            // All work is already done above
            return true;
        default:
            throw new IllegalStateException("Invalid action");
        }
    }

    @Override
    public RaRequestsSearchResponse searchForApprovalRequests(final AuthenticationToken authenticationToken, final RaRequestsSearchRequest request) {
        final RaRequestsSearchResponse response = new RaRequestsSearchResponse();
        final List<CAInfo> authorizedCas = getAuthorizedCas(authenticationToken);
        if (authorizedCas.size() == 0) {
            return response; // not authorized to any CAs. return empty response
        }
        final Map<Integer,String> caIdToNameMap = new HashMap<>();
        for (final CAInfo cainfo : authorizedCas) {
            caIdToNameMap.put(cainfo.getCAId(), cainfo.getName());
        }

        if (!request.isSearchingWaitingForMe() && !request.isSearchingPending() && !request.isSearchingHistorical() && !request.isSearchingExpired()) {
            return response; // not searching for anything. return empty response
        }

        final List<ApprovalDataVO> approvals;
        try {
            String endEntityProfileAuthorizationString = getEndEntityProfileAuthorizationString(authenticationToken, AccessRulesConstants.APPROVE_END_ENTITY);
            RAAuthorization raAuthorization = new RAAuthorization(authenticationToken, globalConfigurationSession,
                    authorizationSession, caSession, endEntityProfileSession);
            approvals = approvalSession.queryByStatus(request.isSearchingWaitingForMe() || request.isSearchingPending(), request.isSearchingHistorical(),
                    request.isSearchingExpired(), request.getStartDate(), request.getEndDate(), request.getExpiresBefore(), 0, 100, raAuthorization.getCAAuthorizationString(), endEntityProfileAuthorizationString);
        } catch (AuthorizationDeniedException e) {
            // Not currently ever thrown by query()
            throw new IllegalStateException(e);
        }
        final Date now = new Date();

        if (log.isDebugEnabled()) {
            log.debug("Got " + approvals.size() + " approvals from Master API");
        }

        if (approvals.size() >= 100) {
            response.setMightHaveMoreResults(true);
        }

        for (final ApprovalDataVO advo : approvals) {
            final List<ApprovalDataText> requestDataLite = advo.getApprovalRequest().getNewRequestDataAsText(authenticationToken); // this method isn't guaranteed to return the full information
            final RaEditableRequestData editableData = getRequestEditableData(authenticationToken, advo);
            // We don't pass the end entity profile or certificate profile details for each approval request, when searching.
            // That information is only needed when viewing the details or editing a request.
            final RaApprovalRequestInfo ari = new RaApprovalRequestInfo(authenticationToken, caIdToNameMap.get(advo.getCAId()), null, null, null,
                    advo, requestDataLite, editableData);

            // Check if this approval should be included in the search results
            boolean include = false;
            if (request.getIncludeOtherAdmins()) {
                include = (request.isSearchingWaitingForMe() && ari.isWaitingForFirstApproval(now)) ||
                        (request.isSearchingPending() && ari.isInProgress(now)) ||
                        (request.isSearchingHistorical() && ari.isProcessed()) ||
                        (request.isSearchingExpired() && ari.isExpired(now));
            } else {
                include = (request.isSearchingWaitingForMe() && ari.isWaitingForMe(authenticationToken)) ||
                    (request.isSearchingPending() && ari.isPending(authenticationToken)) ||
                    (request.isSearchingHistorical() && ari.isProcessed()) ||
                    (request.isSearchingExpired() && ari.isExpired(now));
            }

            if (include) {
                response.getApprovalRequests().add(ari);
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("Returning " + response.getApprovalRequests().size() + " approvals from search");
        }
        return response;
    }

    // TODO this method is copied from RAAuthorization because we couldn't use ComplexAccessControlSession.
    // We should find a way to use ComplexAccessControlSession here instead
    private String getEndEntityProfileAuthorizationString(AuthenticationToken authenticationToken, String endentityAccessRule) throws AuthorizationDeniedException {
        // i.e approvals with endentityprofile ApprovalDataVO.ANY_ENDENTITYPROFILE
        boolean authorizedToApproveCAActions = authorizationSession.isAuthorizedNoLogging(authenticationToken, AccessRulesConstants.REGULAR_APPROVECAACTION);
        // i.e approvals with endentityprofile not ApprovalDataVO.ANY_ENDENTITYPROFILE
        boolean authorizedToApproveRAActions = authorizationSession.isAuthorizedNoLogging(authenticationToken, AccessRulesConstants.REGULAR_APPROVEENDENTITY);
        boolean authorizedToAudit = authorizationSession.isAuthorizedNoLogging(authenticationToken, AuditLogRules.VIEW.resource());

        if (!authorizedToApproveCAActions && !authorizedToApproveRAActions && !authorizedToAudit) {
            throw new AuthorizationDeniedException(authenticationToken + " not authorized to query for approvals: "+authorizedToApproveCAActions+", "+authorizedToApproveRAActions+", "+authorizedToAudit);
        }

        String endentityauth = null;
        GlobalConfiguration globalconfiguration = (GlobalConfiguration) globalConfigurationSession.getCachedConfiguration(GlobalConfiguration.GLOBAL_CONFIGURATION_ID);
        if (globalconfiguration.getEnableEndEntityProfileLimitations()){
            endentityauth = getEndEntityProfileAuthorizationString(authenticationToken, true, endentityAccessRule);
            if(authorizedToApproveCAActions && authorizedToApproveRAActions){
                endentityauth = getEndEntityProfileAuthorizationString(authenticationToken, true, endentityAccessRule);
                if(endentityauth != null){
                  endentityauth = "(" + getEndEntityProfileAuthorizationString(authenticationToken, false, endentityAccessRule) + " OR endEntityProfileId=" + ApprovalDataVO.ANY_ENDENTITYPROFILE + " ) ";
                }
            }else if (authorizedToApproveCAActions) {
                endentityauth = " endEntityProfileId=" + ApprovalDataVO.ANY_ENDENTITYPROFILE;
            }else if (authorizedToApproveRAActions) {
                endentityauth = getEndEntityProfileAuthorizationString(authenticationToken, true, endentityAccessRule);
            }

        }
        return endentityauth == null ? endentityauth : endentityauth.trim();
    }

    // TODO this method is copied from RAAuthorization because we couldn't use ComplexAccessControlSession.
    // We should find a way to use ComplexAccessControlSession here instead
    private String getEndEntityProfileAuthorizationString(AuthenticationToken authenticationToken, boolean includeparanteses, String endentityAccessRule){
        String authendentityprofilestring=null;
          Collection<Integer> profileIds = new ArrayList<>(endEntityProfileSession.getEndEntityProfileIdToNameMap().keySet());
          Collection<Integer> result = getAuthorizedEndEntityProfileIds(authenticationToken, AccessRulesConstants.VIEW_END_ENTITY, profileIds);
          result.retainAll(this.endEntityProfileSession.getAuthorizedEndEntityProfileIds(authenticationToken, endentityAccessRule));
          Iterator<Integer> iter = result.iterator();

          while(iter.hasNext()){
            if(authendentityprofilestring == null) {
              authendentityprofilestring = " endEntityProfileId = " + iter.next().toString();
            } else {
              authendentityprofilestring = authendentityprofilestring + " OR endEntityProfileId = " + iter.next().toString();
            }
          }

          if(authendentityprofilestring != null) {
            authendentityprofilestring = "( " + authendentityprofilestring + " )";
          }

        return authendentityprofilestring;
      }

    // TODO this method is copied from ComplexAccessControlSession. We should find a way to use ComplexAccessControlSession here instead
    private Collection<Integer> getAuthorizedEndEntityProfileIds(AuthenticationToken authenticationToken, String rapriviledge,
            Collection<Integer> availableEndEntityProfileId) {
        ArrayList<Integer> returnval = new ArrayList<>();
        for (final Integer profileid : availableEndEntityProfileId) {
            if (authorizationSession.isAuthorizedNoLogging(authenticationToken, AccessRulesConstants.ENDENTITYPROFILEPREFIX + profileid + rapriviledge)) {
                returnval.add(profileid);
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("Administrator " + authenticationToken + " is not authorized to end entity profile: " + profileid);
                }
            }
        }
        return returnval;
    }

    private boolean authorizedToCert(final AuthenticationToken authenticationToken, final CertificateDataWrapper cdw) {
        if (!caSession.authorizedToCANoLogging(authenticationToken, cdw.getCertificateData().getIssuerDN().hashCode())) {
            return false;
        }
        // Check EEP authorization (allow an highly privileged admin, e.g. superadmin, that can access all profiles to ignore this check
        // so certificates can still be accessed by this admin even after a EEP has been removed.
        // Also, if we have access to the EMPTY profile, then we allow access to certificates with zero/null profile IDs, so they can at least be revoked.
        final Collection<Integer> authorizedEepIds = new ArrayList<>(endEntityProfileSession.getAuthorizedEndEntityProfileIds(authenticationToken, AccessRulesConstants.VIEW_END_ENTITY));
        final boolean accessAnyEepAvailable = authorizedEepIds.containsAll(endEntityProfileSession.getEndEntityProfileIdToNameMap().keySet());
        if (authorizedEepIds.contains(EndEntityConstants.EMPTY_END_ENTITY_PROFILE)) {
            authorizedEepIds.add(EndEntityConstants.NO_END_ENTITY_PROFILE);
        }
        if (!accessAnyEepAvailable && !authorizedEepIds.contains(Integer.valueOf(cdw.getCertificateData().getEndEntityProfileIdOrZero()))) {
            return false;
        }
        return true;
    }

    @Override
    public CertificateDataWrapper searchForCertificate(final AuthenticationToken authenticationToken, final String fingerprint) {
        final CertificateDataWrapper cdw = certificateStoreSession.getCertificateData(fingerprint);
        if (cdw==null || !authorizedToCert(authenticationToken, cdw)) {
            return null;
        }
        return cdw;
    }

    @Override
    public CertificateDataWrapper searchForCertificateByIssuerAndSerial(final AuthenticationToken authenticationToken, final String issuerDN, final String serno) {
        final CertificateDataWrapper cdw = certificateStoreSession.getCertificateDataByIssuerAndSerno(issuerDN, new BigInteger(serno, 16));
        if (cdw==null || !authorizedToCert(authenticationToken, cdw)) {
            return null;
        }
        return cdw;
    }

    @SuppressWarnings("unchecked")
    @Override
    public RaCertificateSearchResponse searchForCertificates(AuthenticationToken authenticationToken, RaCertificateSearchRequest request) {
        final RaCertificateSearchResponse response = new RaCertificateSearchResponse();
        final List<Integer> authorizedLocalCaIds = new ArrayList<>(caSession.getAuthorizedCaIds(authenticationToken));
        // Only search a subset of the requested CAs if requested
        if (!request.getCaIds().isEmpty()) {
            authorizedLocalCaIds.retainAll(request.getCaIds());
        }
        final List<String> issuerDns = new ArrayList<>();
        for (final int caId : authorizedLocalCaIds) {
            try {
                final String issuerDn = CertTools.stringToBCDNString(StringTools.strip(caSession.getCAInfoInternal(caId).getSubjectDN()));
                issuerDns.add(issuerDn);
            } catch (CADoesntExistsException e) {
                log.warn("Could not find CA with id " + caId + " during search operation. " + e.getMessage());
            }
        }
        if (issuerDns.isEmpty()) {
            // Empty response since there were no authorized CAs
            if (log.isDebugEnabled()) {
                log.debug("Client '"+authenticationToken+"' was not authorized to any of the requested CAs and the search request will be dropped.");
            }
            return response;
        }
        // Check Certificate Profile authorization
        final List<Integer> authorizedCpIds = new ArrayList<>(certificateProfileSession.getAuthorizedCertificateProfileIds(authenticationToken, 0));
        final boolean accessAnyCpAvailable = authorizedCpIds.containsAll(certificateProfileSession.getCertificateProfileIdToNameMap().keySet());
        if (!request.getCpIds().isEmpty()) {
            authorizedCpIds.retainAll(request.getCpIds());
        }
        if (authorizedCpIds.isEmpty()) {
            // Empty response since there were no authorized Certificate Profiles
            if (log.isDebugEnabled()) {
                log.debug("Client '"+authenticationToken+"' was not authorized to any of the requested CPs and the search request will be dropped.");
            }
            return response;
        }
        // Check End Entity Profile authorization
        final Collection<Integer> authorizedEepIds = new ArrayList<>(endEntityProfileSession.getAuthorizedEndEntityProfileIds(authenticationToken, AccessRulesConstants.VIEW_END_ENTITY));
        final boolean accessAnyEepAvailable = authorizedEepIds.containsAll(endEntityProfileSession.getEndEntityProfileIdToNameMap().keySet());
        if (!request.getEepIds().isEmpty()) {
            authorizedEepIds.retainAll(request.getEepIds());
        }
        if (authorizedEepIds.isEmpty()) {
            // Empty response since there were no authorized End Entity Profiles
            if (log.isDebugEnabled()) {
                log.debug("Client '"+authenticationToken+"' was not authorized to any of the requested EEPs and the search request will be dropped.");
            }
            return response;
        }
        // If we have access to the EMPTY profile, then allow viewing certificates with zero/null profile IDs, so they can at least be revoked
        if (authorizedEepIds.contains(EndEntityConstants.EMPTY_END_ENTITY_PROFILE)) {
            authorizedEepIds.add(EndEntityConstants.NO_END_ENTITY_PROFILE);
            authorizedCpIds.add(CertificateProfileConstants.NO_CERTIFICATE_PROFILE);
        }
        final String subjectDnSearchString = request.getSubjectDnSearchString();
        final String subjectAnSearchString = request.getSubjectAnSearchString();
        final String usernameSearchString = request.getUsernameSearchString();
        final String serialNumberSearchStringFromDec = request.getSerialNumberSearchStringFromDec();
        final String serialNumberSearchStringFromHex = request.getSerialNumberSearchStringFromHex();
        final StringBuilder sb = new StringBuilder("SELECT a.fingerprint FROM CertificateData a WHERE (a.issuerDN IN (:issuerDN))");
        if (!subjectDnSearchString.isEmpty() || !subjectAnSearchString.isEmpty() || !usernameSearchString.isEmpty() ||
                !serialNumberSearchStringFromDec.isEmpty() || !serialNumberSearchStringFromHex.isEmpty()) {
            sb.append(" AND (");
            boolean firstAppended = false;
            if (!subjectDnSearchString.isEmpty()) {
                sb.append("UPPER(a.subjectDN) LIKE :subjectDN");
                firstAppended = true;
            }
            if (!subjectAnSearchString.isEmpty()) {
                if (firstAppended) {
                    sb.append(" OR ");
                } else {
                    firstAppended = true;
                }
                sb.append("a.subjectAltName LIKE :subjectAltName");
            }
            if (!usernameSearchString.isEmpty()) {
                if (firstAppended) {
                    sb.append(" OR ");
                } else {
                    firstAppended = true;
                }
                sb.append("UPPER(a.username) LIKE :username");
            }
            if (!serialNumberSearchStringFromDec.isEmpty()) {
                if (firstAppended) {
                    sb.append(" OR ");
                } else {
                    firstAppended = true;
                }
                sb.append("a.serialNumber LIKE :serialNumberDec");
            }
            if (!serialNumberSearchStringFromHex.isEmpty()) {
                if (firstAppended) {
                    sb.append(" OR ");
                }
                sb.append("a.serialNumber LIKE :serialNumberHex");
            }
            sb.append(")");
        }
        // NOTE: notBefore is not indexed.. we might want to disallow such search.
        if (request.isIssuedAfterUsed()) {
            sb.append(" AND (a.notBefore > :issuedAfter)");
        }
        if (request.isIssuedBeforeUsed()) {
            sb.append(" AND (a.notBefore < :issuedBefore)");
        }
        if (request.isExpiresAfterUsed()) {
            sb.append(" AND (a.expireDate > :expiresAfter)");
        }
        if (request.isExpiresBeforeUsed()) {
            sb.append(" AND (a.expireDate < :expiresBefore)");
        }
        // NOTE: revocationDate is not indexed.. we might want to disallow such search.
        if (request.isRevokedAfterUsed()) {
            sb.append(" AND (a.revocationDate > :revokedAfter)");
        }
        if (request.isRevokedBeforeUsed()) {
            sb.append(" AND (a.revocationDate < :revokedBefore)");
        }
        if (!request.getStatuses().isEmpty()) {
            sb.append(" AND (a.status IN (:status))");
            if ((request.getStatuses().contains(CertificateConstants.CERT_REVOKED) || request.getStatuses().contains(CertificateConstants.CERT_ARCHIVED)) &&
                    !request.getRevocationReasons().isEmpty()) {
                sb.append(" AND (a.revocationReason IN (:revocationReason))");
            }
        }
        // Don't constrain results to certain certificate profiles if root access is available and "any" CP is requested
        if (!accessAnyCpAvailable || !request.getCpIds().isEmpty()) {
            sb.append(" AND (a.certificateProfileId IN (:certificateProfileId))");
        }
        // Don't constrain results to certain end entity profiles if root access is available and "any" EEP is requested
        if (!accessAnyEepAvailable || !request.getEepIds().isEmpty()) {
            sb.append(" AND (a.endEntityProfileId IN (:endEntityProfileId))");
        }
        final Query query = entityManager.createQuery(sb.toString());
        query.setParameter("issuerDN", issuerDns);
        if (!accessAnyCpAvailable || !request.getCpIds().isEmpty()) {
            query.setParameter("certificateProfileId", authorizedCpIds);
        }
        if (!accessAnyEepAvailable || !request.getEepIds().isEmpty()) {
            query.setParameter("endEntityProfileId", authorizedEepIds);
        }
        if (log.isDebugEnabled()) {
            log.debug(" issuerDN: " + Arrays.toString(issuerDns.toArray()));
            if (!accessAnyCpAvailable || !request.getCpIds().isEmpty()) {
                log.debug(" certificateProfileId: " + Arrays.toString(authorizedCpIds.toArray()));
            } else {
                log.debug(" certificateProfileId: Any (even deleted) profile(s) due to root access.");
            }
            if (!accessAnyEepAvailable || !request.getEepIds().isEmpty()) {
                log.debug(" endEntityProfileId: " + Arrays.toString(authorizedEepIds.toArray()));
            } else {
                log.debug(" endEntityProfileId: Any (even deleted) profile(s) due to root access.");
            }
        }
        if (!subjectDnSearchString.isEmpty()) {
            if (request.isSubjectDnSearchExact()) {
                query.setParameter("subjectDN", subjectDnSearchString.toUpperCase());
            } else {
                query.setParameter("subjectDN", "%" + subjectDnSearchString.toUpperCase() + "%");
            }
        }
        if (!subjectAnSearchString.isEmpty()) {
            if (request.isSubjectAnSearchExact()) {
                query.setParameter("subjectAltName", subjectAnSearchString);
            } else {
                query.setParameter("subjectAltName", "%" + subjectAnSearchString + "%");
            }
        }
        if (!usernameSearchString.isEmpty()) {
            if (request.isUsernameSearchExact()) {
                query.setParameter("username", usernameSearchString.toUpperCase());
            } else {
                query.setParameter("username", "%" + usernameSearchString.toUpperCase() + "%");
            }
        }
        if (!serialNumberSearchStringFromDec.isEmpty()) {
            query.setParameter("serialNumberDec", serialNumberSearchStringFromDec);
            if (log.isDebugEnabled()) {
                log.debug(" serialNumberDec: " + serialNumberSearchStringFromDec);
            }
        }
        if (!serialNumberSearchStringFromHex.isEmpty()) {
            query.setParameter("serialNumberHex", serialNumberSearchStringFromHex);
            if (log.isDebugEnabled()) {
                log.debug(" serialNumberHex: " + serialNumberSearchStringFromHex);
            }
        }
        if (request.isIssuedAfterUsed()) {
            query.setParameter("issuedAfter", request.getIssuedAfter());
        }
        if (request.isIssuedBeforeUsed()) {
            query.setParameter("issuedBefore", request.getIssuedBefore());
        }
        if (request.isExpiresAfterUsed()) {
            query.setParameter("expiresAfter", request.getExpiresAfter());
        }
        if (request.isExpiresBeforeUsed()) {
            query.setParameter("expiresBefore", request.getExpiresBefore());
        }
        if (request.isRevokedAfterUsed()) {
            query.setParameter("revokedAfter", request.getRevokedAfter());
        }
        if (request.isRevokedBeforeUsed()) {
            query.setParameter("revokedBefore", request.getRevokedBefore());
        }
        if (!request.getStatuses().isEmpty()) {
            query.setParameter("status", request.getStatuses());
            if ((request.getStatuses().contains(CertificateConstants.CERT_REVOKED) || request.getStatuses().contains(CertificateConstants.CERT_ARCHIVED)) &&
                    !request.getRevocationReasons().isEmpty()) {
                query.setParameter("revocationReason", request.getRevocationReasons());
            }
        }
        final int maxResults = Math.min(getGlobalCesecoreConfiguration().getMaximumQueryCount(), request.getMaxResults());
        final int offset = request.getPageNumber() * maxResults;
        query.setMaxResults(maxResults);
        query.setFirstResult(offset);

        /* Try to use the non-portable hint (depends on DB and JDBC driver) to specify how long in milliseconds the query may run. Possible behaviors:
         * - The hint is ignored
         * - A QueryTimeoutException is thrown
         * - A PersistenceException is thrown (and the transaction which don't have here is marked for roll-back)
         */
        final long queryTimeout = getGlobalCesecoreConfiguration().getMaximumQueryTimeout();
        if (queryTimeout>0L) {
            query.setHint("javax.persistence.query.timeout", String.valueOf(queryTimeout));
        }
        final List<String> fingerprints;
        try {
            fingerprints = query.getResultList();
            for (final String fingerprint : fingerprints) {
                response.getCdws().add(certificateStoreSession.getCertificateData(fingerprint));
            }
            response.setMightHaveMoreResults(fingerprints.size()==maxResults);
            if (log.isDebugEnabled()) {
                log.debug("Certificate search query: " + sb.toString() + " LIMIT " + maxResults + " \u2192 " + fingerprints.size() + " results. queryTimeout=" + queryTimeout + "ms");
            }
        } catch (QueryTimeoutException e) {
            // Query.toString() does not return the SQL query executed just a java object hash. If Hibernate is being used we can get it using:
            // query.unwrap(org.hibernate.Query.class).getQueryString()
            // We don't have access to hibernate when building this class though, all querying should be moved to the ejbca-entity package.
            // See ECA-5341
            final Query q = e.getQuery();
            String queryString = null;
            if (q != null) {
                queryString = q.toString();
            }
//            try {
//                queryString = e.getQuery().unwrap(org.hibernate.Query.class).getQueryString();
//            } catch (PersistenceException pe) {
//                log.debug("Query.unwrap(org.hibernate.Query.class) is not supported by JPA provider");
//            }
            log.info("Requested search query by " + authenticationToken +  " took too long. Query was '" + queryString + "'. " + e.getMessage());
            response.setMightHaveMoreResults(true);
        } catch (PersistenceException e) {
            log.info("Requested search query by " + authenticationToken +  " failed, possibly due to timeout. " + e.getMessage());
            response.setMightHaveMoreResults(true);
        }
        return response;
    }

    @SuppressWarnings("unchecked")
    @Override
    public RaEndEntitySearchResponse searchForEndEntities(AuthenticationToken authenticationToken, RaEndEntitySearchRequest request) {
        final RaEndEntitySearchResponse response = new RaEndEntitySearchResponse();
        final List<Integer> authorizedLocalCaIds = new ArrayList<>(caSession.getAuthorizedCaIds(authenticationToken));
        // Only search a subset of the requested CAs if requested
        if (!request.getCaIds().isEmpty()) {
            authorizedLocalCaIds.retainAll(request.getCaIds());
        }
        if (authorizedLocalCaIds.isEmpty()) {
            // Empty response since there were no authorized CAs
            if (log.isDebugEnabled()) {
                log.debug("Client '"+authenticationToken+"' was not authorized to any of the requested CAs and the search request will be dropped.");
            }
            return response;
        }
        // Check Certificate Profile authorization
        final List<Integer> authorizedCpIds = new ArrayList<>(certificateProfileSession.getAuthorizedCertificateProfileIds(authenticationToken, 0));
        final boolean accessAnyCpAvailable = authorizedCpIds.containsAll(certificateProfileSession.getCertificateProfileIdToNameMap().keySet());
        if (!request.getCpIds().isEmpty()) {
            authorizedCpIds.retainAll(request.getCpIds());
        }
        if (authorizedCpIds.isEmpty()) {
            // Empty response since there were no authorized Certificate Profiles
            if (log.isDebugEnabled()) {
                log.debug("Client '"+authenticationToken+"' was not authorized to any of the requested CPs and the search request will be dropped.");
            }
            return response;
        }
        // Check End Entity Profile authorization
        final Collection<Integer> authorizedEepIds = new ArrayList<>(endEntityProfileSession.getAuthorizedEndEntityProfileIds(authenticationToken, AccessRulesConstants.VIEW_END_ENTITY));
        final boolean accessAnyEepAvailable = authorizedEepIds.containsAll(endEntityProfileSession.getEndEntityProfileIdToNameMap().keySet());
        if (!request.getEepIds().isEmpty()) {
            authorizedEepIds.retainAll(request.getEepIds());
        }
        if (authorizedEepIds.isEmpty()) {
            // Empty response since there were no authorized End Entity Profiles
            if (log.isDebugEnabled()) {
                log.debug("Client '"+authenticationToken+"' was not authorized to any of the requested EEPs and the search request will be dropped.");
            }
            return response;
        }
        final String subjectDnSearchString = request.getSubjectDnSearchString();
        final String subjectAnSearchString = request.getSubjectAnSearchString();
        final String usernameSearchString = request.getUsernameSearchString();
        final StringBuilder sb = new StringBuilder("SELECT a.username FROM UserData a WHERE (a.caId IN (:caId))");
        if (!subjectDnSearchString.isEmpty() || !subjectAnSearchString.isEmpty() || !usernameSearchString.isEmpty()) {
            sb.append(" AND (");
            boolean firstAppended = false;
            if (!subjectDnSearchString.isEmpty()) {
                sb.append("UPPER(a.subjectDN) LIKE :subjectDN");
                firstAppended = true;
            }
            if (!subjectAnSearchString.isEmpty()) {
                if (firstAppended) {
                    sb.append(" OR ");
                } else {
                    firstAppended = true;
                }
                sb.append("a.subjectAltName LIKE :subjectAltName");
            }
            if (!usernameSearchString.isEmpty()) {
                if (firstAppended) {
                    sb.append(" OR ");
                } else {
                    firstAppended = true;
                }
                sb.append("UPPER(a.username) LIKE :username");
            }
            sb.append(")");
        }

        if (request.isModifiedAfterUsed()) {
            sb.append(" AND (a.timeModified > :modifiedAfter)");
        }
        if (request.isModifiedBeforeUsed()) {
            sb.append(" AND (a.timeModified < :modifiedBefore)");
        }
        if (!request.getStatuses().isEmpty()) {
            sb.append(" AND (a.status IN (:status))");
        }
        // Don't constrain results to certain end entity profiles if root access is available and "any" CP is requested
        if (!accessAnyCpAvailable || !request.getCpIds().isEmpty()) {
            sb.append(" AND (a.certificateProfileId IN (:certificateProfileId))");
        }
        // Don't constrain results to certain end entity profiles if root access is available and "any" EEP is requested
        if (!accessAnyEepAvailable || !request.getEepIds().isEmpty()) {
            sb.append(" AND (a.endEntityProfileId IN (:endEntityProfileId))");
        }
        final Query query = entityManager.createQuery(sb.toString());
        query.setParameter("caId", authorizedLocalCaIds);
        if (!accessAnyCpAvailable || !request.getCpIds().isEmpty()) {
            query.setParameter("certificateProfileId", authorizedCpIds);
        }
        if (!accessAnyEepAvailable || !request.getEepIds().isEmpty()) {
            query.setParameter("endEntityProfileId", authorizedEepIds);
        }
        if (log.isDebugEnabled()) {
            log.debug(" CA IDs: " + Arrays.toString(authorizedLocalCaIds.toArray()));
            if (!accessAnyCpAvailable || !request.getCpIds().isEmpty()) {
                log.debug(" certificateProfileId: " + Arrays.toString(authorizedCpIds.toArray()));
            } else {
                log.debug(" certificateProfileId: Any (even deleted) profile(s) due to root access.");
            }
            if (!accessAnyEepAvailable || !request.getEepIds().isEmpty()) {
                log.debug(" endEntityProfileId: " + Arrays.toString(authorizedEepIds.toArray()));
            } else {
                log.debug(" endEntityProfileId: Any (even deleted) profile(s) due to root access.");
            }
        }
        if (!subjectDnSearchString.isEmpty()) {
            if (request.isSubjectDnSearchExact()) {
                query.setParameter("subjectDN", subjectDnSearchString.toUpperCase());
            } else {
                query.setParameter("subjectDN", "%" + subjectDnSearchString.toUpperCase() + "%");
            }
        }
        if (!subjectAnSearchString.isEmpty()) {
            if (request.isSubjectAnSearchExact()) {
                query.setParameter("subjectAltName", subjectAnSearchString);
            } else {
                query.setParameter("subjectAltName", "%" + subjectAnSearchString + "%");
            }
        }
        if (!usernameSearchString.isEmpty()) {
            if (request.isUsernameSearchExact()) {
                query.setParameter("username", usernameSearchString.toUpperCase());
            } else {
                query.setParameter("username", "%" + usernameSearchString.toUpperCase() + "%");
            }
        }
        if (request.isModifiedAfterUsed()) {
            query.setParameter("modifiedAfter", request.getModifiedAfter());
        }
        if (request.isModifiedBeforeUsed()) {
            query.setParameter("modifiedBefore", request.getModifiedBefore());
        }
        if (!request.getStatuses().isEmpty()) {
            query.setParameter("status", request.getStatuses());
        }
        final int maxResults = Math.min(getGlobalCesecoreConfiguration().getMaximumQueryCount(), request.getMaxResults());
        final int offset = maxResults * request.getPageNumber();
        query.setMaxResults(maxResults);
        query.setFirstResult(offset);
        /* Try to use the non-portable hint (depends on DB and JDBC driver) to specify how long in milliseconds the query may run. Possible behaviors:
         * - The hint is ignored
         * - A QueryTimeoutException is thrown
         * - A PersistenceException is thrown (and the transaction which don't have here is marked for roll-back)
         */
        final long queryTimeout = getGlobalCesecoreConfiguration().getMaximumQueryTimeout();
        if (queryTimeout>0L) {
            query.setHint("javax.persistence.query.timeout", String.valueOf(queryTimeout));
        }
        final List<String> usernames;
        try {
            usernames = query.getResultList();
            for (final String username : usernames) {
                response.getEndEntities().add(endEntityAccessSession.findUser(username));
            }
            response.setMightHaveMoreResults(usernames.size()==maxResults);
            if (log.isDebugEnabled()) {
                log.debug("Certificate search query: " + sb.toString() + " LIMIT " + maxResults + " \u2192 " + usernames.size() + " results. queryTimeout=" + queryTimeout + "ms");
            }
        } catch (QueryTimeoutException e) {
            log.info("Requested search query by " + authenticationToken +  " took too long. Query was " + e.getQuery().toString() + ". " + e.getMessage());
            response.setMightHaveMoreResults(true);
        } catch (PersistenceException e) {
            log.info("Requested search query by " + authenticationToken +  " failed, possibly due to timeout. " + e.getMessage());
            response.setMightHaveMoreResults(true);
        }
        return response;
    }

    @Override
    public RaRoleSearchResponse searchForRoles(AuthenticationToken authenticationToken, RaRoleSearchRequest request) {
        // TODO optimize this (ECA-5721), should filter with a database query
        final List<Role> authorizedRoles = getAuthorizedRoles(authenticationToken);
        final RaRoleSearchResponse searchResponse = new RaRoleSearchResponse();
        final String searchString = request.getGenericSearchString();
        for (final Role role : authorizedRoles) {
            if (searchString == null || StringUtils.containsIgnoreCase(role.getRoleName(), searchString) ||
                    (role.getNameSpace() != null && StringUtils.containsIgnoreCase(role.getNameSpace(), searchString))) {
                searchResponse.getRoles().add(role);
            }
        }
        return searchResponse;
    }

    @SuppressWarnings("unchecked")
    @Override
    public RaRoleMemberSearchResponse searchForRoleMembers(AuthenticationToken authenticationToken, RaRoleMemberSearchRequest request) {
        final RaRoleMemberSearchResponse response = new RaRoleMemberSearchResponse();

        final List<Integer> authorizedLocalCaIds = new ArrayList<>(caSession.getAuthorizedCaIds(authenticationToken));
        authorizedLocalCaIds.add(RoleMember.NO_ISSUER);
        // Only search a subset of the requested CAs if requested
        if (!request.getCaIds().isEmpty()) {
            authorizedLocalCaIds.retainAll(request.getCaIds());
        }

        // Dito for roles
        final List<Integer> authorizedLocalRoleIds = new ArrayList<>();
        for (final Role role : roleSession.getAuthorizedRoles(authenticationToken)) {
            final int roleId = role.getRoleId();
            if (request.getRoleIds().isEmpty() || request.getRoleIds().contains(roleId)) {
                authorizedLocalRoleIds.add(roleId);
            }
        }
        if (request.getRoleIds().contains(RoleMember.NO_ROLE)) {
            authorizedLocalRoleIds.add(RoleMember.NO_ROLE);
        }

        // Token types
        final List<String> authorizedLocalTokenTypes = new ArrayList<>(getAvailableRoleMemberTokenTypes(authenticationToken).keySet());
        if (!request.getTokenTypes().isEmpty()) {
            authorizedLocalTokenTypes.retainAll(request.getTokenTypes());
        }

        if (authorizedLocalCaIds.isEmpty()) {
            log.debug("No authorized CAs found for cleint " + authenticationToken + ". Returning empty response in role member search");
            return response;
        }
        if (authorizedLocalRoleIds.isEmpty()) {
            log.debug("No authorized Roles found for cleint " + authenticationToken + " Returning empty response in role member search");
            return response;
        }
        if (authorizedLocalTokenTypes.isEmpty()) {
            log.debug("No authorized token types found for cleint " + authenticationToken + " Returning empty response in role member search");
            return response;
        }

        // Build query
        final StringBuilder sb = new StringBuilder("SELECT a FROM RoleMemberData a WHERE a.tokenIssuerId IN (:caId) AND a.roleId IN (:roleId) AND a.tokenType IN (:tokenType)");
        // TODO only search by exact tokenMatchValue if it seems to be a serial number?
        if (!StringUtils.isEmpty(request.getGenericSearchString())) {
            sb.append(" AND (a.tokenMatchValueColumn LIKE :searchStringInexact OR a.descriptionColumn LIKE :searchStringInexact)");
        }
        final Query query = entityManager.createQuery(sb.toString());
        query.setParameter("caId", authorizedLocalCaIds);
        query.setParameter("roleId", authorizedLocalRoleIds);
        query.setParameter("tokenType", authorizedLocalTokenTypes);
        if (!StringUtils.isEmpty(request.getGenericSearchString())) {
            //query.setParameter("searchString", request.getGenericSearchString());
            query.setParameter("searchStringInexact", request.getGenericSearchString() + '%');
        }

        final int maxResults = getGlobalCesecoreConfiguration().getMaximumQueryCount();
        query.setMaxResults(maxResults);
        final long queryTimeout = getGlobalCesecoreConfiguration().getMaximumQueryTimeout();
        if (queryTimeout>0L) {
            query.setHint("javax.persistence.query.timeout", String.valueOf(queryTimeout));
        }

        // Execute
        try {
            final List<RoleMemberData> roleMemberDatas = query.getResultList();
            for (final RoleMemberData roleMemberData : roleMemberDatas) {
                response.getRoleMembers().add(roleMemberData.asValueObject());
            }
            response.setMightHaveMoreResults(roleMemberDatas.size()==maxResults);
            if (log.isDebugEnabled()) {
                log.debug("Role Member search query: " + sb.toString() + " LIMIT " + maxResults + " \u2192 " + roleMemberDatas.size() + " results. queryTimeout=" + queryTimeout + "ms");
            }
        } catch (QueryTimeoutException e) {
            log.info("Requested search query by " + authenticationToken +  " took too long. Query was " + e.getQuery().toString() + ". " + e.getMessage());
            response.setMightHaveMoreResults(true);
        } catch (PersistenceException e) {
            log.info("Requested search query by " + authenticationToken +  " failed, possibly due to timeout. " + e.getMessage());
            response.setMightHaveMoreResults(true);
        }
        return response;
    }

    @Override
    public Map<Integer, String> getAuthorizedEndEntityProfileIdsToNameMap(AuthenticationToken authenticationToken) {
        final Collection<Integer> authorizedEepIds = endEntityProfileSession.getAuthorizedEndEntityProfileIds(authenticationToken, AccessRulesConstants.VIEW_END_ENTITY);
        final Map<Integer, String> idToNameMap = endEntityProfileSession.getEndEntityProfileIdToNameMap();
        final Map<Integer, String> authorizedIdToNameMap = new HashMap<>();
        for (final Integer eepId : authorizedEepIds) {
            authorizedIdToNameMap.put(eepId, idToNameMap.get(eepId));
        }
        return authorizedIdToNameMap;
    }

    @Override
    public Map<Integer, String> getAuthorizedCertificateProfileIdsToNameMap(AuthenticationToken authenticationToken) {
        final List<Integer> authorizedCpIds = new ArrayList<>(certificateProfileSession.getAuthorizedCertificateProfileIds(authenticationToken, 0));
        // There is no reason to return a certificate profile if it is not present in one of the authorized EEPs
        final Collection<Integer> authorizedEepIds = endEntityProfileSession.getAuthorizedEndEntityProfileIds(authenticationToken, AccessRulesConstants.VIEW_END_ENTITY);
        final Set<Integer> cpIdsInAuthorizedEeps = new HashSet<>();
        for (final Integer eepId : authorizedEepIds) {
            final EndEntityProfile eep = endEntityProfileSession.getEndEntityProfile(eepId);
            for (final String availableCertificateProfileId : eep.getAvailableCertificateProfileIds()) {
                cpIdsInAuthorizedEeps.add(Integer.parseInt(availableCertificateProfileId));
            }
        }
        authorizedCpIds.retainAll(cpIdsInAuthorizedEeps);
        final Map<Integer, String> idToNameMap = certificateProfileSession.getCertificateProfileIdToNameMap();
        final Map<Integer, String> authorizedIdToNameMap = new HashMap<>();
        for (final Integer cpId : authorizedCpIds) {
            authorizedIdToNameMap.put(cpId, idToNameMap.get(cpId));
        }
        return authorizedIdToNameMap;
    }

    @Override
    public IdNameHashMap<EndEntityProfile> getAuthorizedEndEntityProfiles(final AuthenticationToken authenticationToken, final String endEntityAccessRule) {
        Collection<Integer> ids = endEntityProfileSession.getAuthorizedEndEntityProfileIds(authenticationToken, endEntityAccessRule);
        Map<Integer, String> idToNameMap = endEntityProfileSession.getEndEntityProfileIdToNameMap();
        IdNameHashMap<EndEntityProfile> authorizedEndEntityProfiles = new IdNameHashMap<>();
        for (Integer id : ids){
            authorizedEndEntityProfiles.put(id, idToNameMap.get(id), endEntityProfileSession.getEndEntityProfile(id));
        }
        return authorizedEndEntityProfiles;
    }

    @Override
    public IdNameHashMap<CertificateProfile> getAuthorizedCertificateProfiles(AuthenticationToken authenticationToken){
        IdNameHashMap<CertificateProfile> authorizedCertificateProfiles = new IdNameHashMap<>();
        List<Integer> authorizedCertificateProfileIds = certificateProfileSession.getAuthorizedCertificateProfileIds(authenticationToken, CertificateConstants.CERTTYPE_ENDENTITY);
        for (Integer certificateProfileId : authorizedCertificateProfileIds){
            CertificateProfile certificateProfile = certificateProfileSession.getCertificateProfile(certificateProfileId);
            String certificateProfilename = certificateProfileSession.getCertificateProfileName(certificateProfileId);
            authorizedCertificateProfiles.put(certificateProfileId, certificateProfilename, certificateProfile);
        }

        return authorizedCertificateProfiles;
    }

    @Override
    public CertificateProfile getCertificateProfile(int id) {
        return certificateProfileSession.getCertificateProfile(id);
    }

    @Override
    public IdNameHashMap<CAInfo> getAuthorizedCAInfos(AuthenticationToken authenticationToken) {
        IdNameHashMap<CAInfo> authorizedCAInfos = new IdNameHashMap<>();
        List<CAInfo> authorizedCAInfosList = caSession.getAuthorizedAndNonExternalCaInfos(authenticationToken);
        for (CAInfo caInfo : authorizedCAInfosList){
            if (caInfo.getStatus() == CAConstants.CA_ACTIVE) {
                authorizedCAInfos.put(caInfo.getCAId(), caInfo.getName(), caInfo);
            }
        }
        return authorizedCAInfos;
    }

    @Override
    public void checkSubjectDn(final AuthenticationToken admin, final EndEntityInformation endEntity) throws AuthorizationDeniedException, EjbcaException{
        KeyToValueHolder<CAInfo> caInfoEntry = getAuthorizedCAInfos(admin).get(endEntity.getCAId());
        if(caInfoEntry == null) {
            log.info("No authorized CAs found for " + admin);
            return;
        }
        try {
            certificateCreateSession.assertSubjectEnforcements(caInfoEntry.getValue(), endEntity);
        } catch (CertificateCreateException e) {
            //Wrapping the CesecoreException.errorCode
            throw new EjbcaException(e);
        }
    }

    @Override
    public boolean addUser(final AuthenticationToken admin, final EndEntityInformation endEntity, final boolean clearpwd) throws AuthorizationDeniedException,
    EjbcaException, WaitingForApprovalException{
        //Authorization
        if (!endEntityManagementSessionLocal.isAuthorizedToEndEntityProfile(admin, endEntity.getEndEntityProfileId(),
                AccessRulesConstants.DELETE_END_ENTITY)) {
            log.warn("Missing *" + AccessRulesConstants.DELETE_END_ENTITY + " rights for user '" + admin
                    + "' to be able to add an end entity (Delete is only needed for clean-up if something goes wrong after an end-entity has been added)");
            return false;
        }

        try {
            endEntityManagementSessionLocal.addUser(admin, endEntity, clearpwd);
        } catch (CesecoreException e) {
            //Wrapping the CesecoreException.errorCode
            throw new EjbcaException(e);
        } catch (EndEntityProfileValidationException e) {
            //Wraps @WebFault Exception based with @NonSensitive EjbcaException based
            throw new EndEntityProfileValidationRaException(e);
        }
        return endEntityAccessSession.findUser(endEntity.getUsername()) != null;
    }

    @Override
    public void deleteUser(final AuthenticationToken admin, final String username) throws AuthorizationDeniedException{
        try {
            endEntityManagementSessionLocal.deleteUser(admin, username);
        } catch (NoSuchEndEntityException | RemoveException e) {
            log.error(e);
        }
    }

    @Override
    public EndEntityInformation searchUser(final AuthenticationToken admin, String username) {
        return endEntityAccessSession.findUser(username);
    }

    @Override
    public void checkUserStatus(AuthenticationToken admin, String username, String password) throws NoSuchEndEntityException, AuthStatusException, AuthLoginException {
        endEntityAuthenticationSessionLocal.authenticateUser(admin, username, password);
    }

    @Override
    public void finishUserAfterLocalKeyRecovery(final AuthenticationToken authenticationToken, final String username, final String password) throws AuthorizationDeniedException, EjbcaException {
        EndEntityInformation userdata = endEntityAccessSession.findUser(username);
        if (userdata == null) {
            throw new EjbcaException(ErrorCode.USER_NOT_FOUND, "User '"+username+"' does not exist");
        }
        if (userdata.getStatus() != EndEntityConstants.STATUS_KEYRECOVERY) {
            throw new EjbcaException(ErrorCode.USER_WRONG_STATUS, "User '"+username+"' is not in KEYRECOVERY status");
        }
        try {
            final GlobalConfiguration globalConfig = (GlobalConfiguration) globalConfigurationSession.getCachedConfiguration(GlobalConfiguration.GLOBAL_CONFIGURATION_ID);
            if (globalConfig.getEnableEndEntityProfileLimitations()) {
                // Check if administrator is authorized to perform key recovery
                endEntityManagementSession.isAuthorizedToEndEntityProfile(authenticationToken, userdata.getEndEntityProfileId(), AccessRulesConstants.KEYRECOVERY_RIGHTS);
            }
            endEntityAuthenticationSessionLocal.authenticateUser(authenticationToken, username, password);
            final boolean shouldFinishUser = caSession.getCAInfo(authenticationToken, userdata.getCAId()).getFinishUser();
            if (shouldFinishUser) {
                    endEntityAuthenticationSessionLocal.finishUser(userdata);
            }

            userdata = endEntityAccessSession.findUser(username);
            if (userdata.getStatus() == EndEntityConstants.STATUS_GENERATED) {
                // We require keyrecovery access. The operation below should not require edit access, so we use an AlwaysAllowLocalAuthenticationToken
                endEntityManagementSessionLocal.setClearTextPassword(new AlwaysAllowLocalAuthenticationToken(
                        new UsernamePrincipal("Implicit authorization from key recovery operation to reset password.")), username, null);
            }
        } catch (NoSuchEndEntityException | CADoesntExistsException | EndEntityProfileValidationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public byte[] generateKeyStore(final AuthenticationToken admin, final EndEntityInformation endEntity) throws AuthorizationDeniedException, EjbcaException {
        KeyStore keyStore;
        try {
            final EndEntityProfile endEntityProfile = endEntityProfileSession.getEndEntityProfile(endEntity.getEndEntityProfileId());
            boolean usekeyrecovery = ((GlobalConfiguration) globalConfigurationSession.getCachedConfiguration(GlobalConfiguration.GLOBAL_CONFIGURATION_ID)).getEnableKeyRecovery();
            EndEntityInformation data = endEntityAccessSession.findUser(endEntity.getUsername());
            if (data == null) {
                throw new EjbcaException(ErrorCode.USER_NOT_FOUND, "User '"+endEntity.getUsername()+"' does not exist");
            }
            final boolean savekeys = data.getKeyRecoverable() && usekeyrecovery && (data.getStatus() != EndEntityConstants.STATUS_KEYRECOVERY);
            final boolean loadkeys = (data.getStatus() == EndEntityConstants.STATUS_KEYRECOVERY) && usekeyrecovery;
            final boolean reusecertificate = endEntityProfile.getReUseKeyRecoveredCertificate();
            final String encodedValidity = endEntity.getExtendedInformation().getCertificateEndTime();
            final Date notAfter = encodedValidity == null ? null : ValidityDate.getDate(encodedValidity, new Date());
            keyStore = keyStoreCreateSessionLocal.generateOrKeyRecoverToken(admin, // Authentication token
                    endEntity.getUsername(), // Username
                    endEntity.getPassword(), // Enrollment code
                    endEntity.getCAId(), // The CA signing the private keys
                    endEntity.getExtendedInformation().getKeyStoreAlgorithmSubType(), // Keylength
                    endEntity.getExtendedInformation().getKeyStoreAlgorithmType(), // Signature algorithm
                    null, // Not valid before
                    notAfter, // Not valid after
                    endEntity.getTokenType() == SecConst.TOKEN_SOFT_JKS, // Type of token
                    loadkeys, // Perform key recovery?
                    savekeys, // Save private keys?
                    reusecertificate, // Reuse recovered cert?
                    endEntity.getEndEntityProfileId()); // Identifier for end entity
        } catch (KeyStoreException | InvalidAlgorithmParameterException | CADoesntExistsException | IllegalKeyException
                | CertificateCreateException | IllegalNameException | CertificateRevokeException | CertificateSerialNumberException
                | CryptoTokenOfflineException | IllegalValidityException | CAOfflineException | InvalidAlgorithmException
                | CustomCertificateSerialNumberException | CertificateException | NoSuchAlgorithmException | InvalidKeySpecException
                | EndEntityProfileValidationException | CertificateSignatureException | NoSuchEndEntityException e) {
            throw new KeyStoreGeneralRaException(e);
        }
        if(endEntity.getTokenType() == EndEntityConstants.TOKEN_SOFT_PEM){
            try(ByteArrayOutputStream outputStream = new ByteArrayOutputStream()){
                outputStream.write(KeyTools.getSinglePemFromKeyStore(keyStore, endEntity.getPassword().toCharArray()));
                return outputStream.toByteArray();
            } catch (IOException | CertificateEncodingException | UnrecoverableKeyException | KeyStoreException | NoSuchAlgorithmException e) {
                log.error(e); //should never happen if keyStore is valid object
            }
        }else{
            try(ByteArrayOutputStream outputStream = new ByteArrayOutputStream()){
                keyStore.store(outputStream, endEntity.getPassword().toCharArray());
                return outputStream.toByteArray();
            } catch (IOException | KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
                log.error(e); //should never happen if keyStore is valid object
            }
        }
        return null;
    }

    @Override
    public byte[] createCertificate(AuthenticationToken authenticationToken, EndEntityInformation endEntityInformation)
            throws AuthorizationDeniedException, EjbcaException {
        if(endEntityInformation.getExtendedInformation() == null || endEntityInformation.getExtendedInformation().getCertificateRequest() == null){
            throw new IllegalArgumentException("Could not find CSR for end entity with username " + endEntityInformation.getUsername() + " CSR must be set under endEntityInformation.extendedInformation.certificateRequest");
        }

        PKCS10RequestMessage req = null;
        req = RequestMessageUtils.genPKCS10RequestMessage(endEntityInformation.getExtendedInformation().getCertificateRequest());
        req.setUsername(endEntityInformation.getUsername());
        req.setPassword(endEntityInformation.getPassword());
        final String encodedValidity = endEntityInformation.getExtendedInformation().getCertificateEndTime();
        req.setNotAfter(encodedValidity == null ? null : ValidityDate.getDate(encodedValidity, new Date()));
        try {
            ResponseMessage resp = signSessionLocal.createCertificate(authenticationToken, req, X509ResponseMessage.class, null);
            X509Certificate cert = CertTools.getCertfromByteArray(resp.getResponseMessage(), X509Certificate.class);
            return cert.getEncoded();
        } catch (NoSuchEndEntityException | CustomCertificateSerialNumberException | CryptoTokenOfflineException | IllegalKeyException
                | CADoesntExistsException | SignRequestException | SignRequestSignatureException | IllegalNameException | CertificateCreateException
                | CertificateRevokeException | CertificateSerialNumberException | IllegalValidityException | CAOfflineException
                | InvalidAlgorithmException | CertificateExtensionException e) {
            throw new EjbcaException(e);
        } catch (CertificateParsingException | CertificateEncodingException e) {
            throw new IllegalStateException("Internal error with creating X509Certificate from CertificateResponseMessage");
        }
    }

    @Override
    public byte[] createCertificateWS(final AuthenticationToken authenticationToken, final UserDataVOWS userdata, final String requestData, final int requestType,
            final String hardTokenSN, final String responseType) throws AuthorizationDeniedException, ApprovalException, EjbcaException,
            EndEntityProfileValidationException {
        try {
            // Some of the session beans are only needed for authentication or certation operations, and are passed as null
            final EndEntityInformation endEntityInformation = ejbcaWSHelperSession.convertUserDataVOWS(authenticationToken, userdata);
            int responseTypeInt = CertificateConstants.CERT_RES_TYPE_CERTIFICATE;
            if (!responseType.equalsIgnoreCase(CertificateHelper.RESPONSETYPE_CERTIFICATE)) {
                if (responseType.equalsIgnoreCase(CertificateHelper.RESPONSETYPE_PKCS7)) {
                    responseTypeInt = CertificateConstants.CERT_RES_TYPE_PKCS7;
                }
                else if (responseType.equalsIgnoreCase(CertificateHelper.RESPONSETYPE_PKCS7WITHCHAIN)) {
                    responseTypeInt = CertificateConstants.CERT_RES_TYPE_PKCS7WITHCHAIN;
                }
                else{
                    throw new NoSuchAlgorithmException("Bad responseType:" + responseType);
                }
            }
            return certificateRequestSession.processCertReq(authenticationToken, endEntityInformation, requestData, requestType, hardTokenSN, responseTypeInt);
        } catch (NotFoundException e) {
            log.debug("EJBCA WebService error", e);
            throw e; // NFE extends EjbcaException
        } catch (CertificateExtensionException e) {
            log.debug("EJBCA WebService error", e);
            throw new EjbcaException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        } catch (InvalidKeyException e) {
            log.debug("EJBCA WebService error", e);
            throw new EjbcaException(ErrorCode.INVALID_KEY, e.getMessage());
        } catch (IllegalKeyException e) {
            log.debug("EJBCA WebService error", e);
            throw new EjbcaException(ErrorCode.ILLEGAL_KEY, e.getMessage());
        } catch (AuthStatusException e) {
            log.debug("EJBCA WebService error", e);
            throw new EjbcaException(ErrorCode.USER_WRONG_STATUS, e.getMessage());
        } catch (AuthLoginException e) {
            log.debug("EJBCA WebService error", e);
            throw new EjbcaException(ErrorCode.LOGIN_ERROR, e.getMessage());
        } catch (SignatureException e) {
            log.debug("EJBCA WebService error", e);
            throw new EjbcaException(ErrorCode.SIGNATURE_ERROR, e.getMessage());
        } catch (SignRequestSignatureException e) {
            log.debug("EJBCA WebService error", e);
            throw new EjbcaException(e.getMessage());
        } catch (InvalidKeySpecException e) {
            log.debug("EJBCA WebService error", e);
            throw new EjbcaException(ErrorCode.INVALID_KEY_SPEC, e.getMessage());
        } catch (NoSuchAlgorithmException | NoSuchProviderException | CertificateException | IOException e) {
            log.debug("EJBCA WebService error", e);
            throw new EjbcaException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        } catch (CesecoreException e) {
            log.debug("EJBCA WebService error", e);
            // Will convert the CESecore exception to an EJBCA exception with the same error code
            throw new EjbcaException(e.getErrorCode(), e);
        } catch (RuntimeException e) {  // EJBException, ClassCastException, ...
            log.debug("EJBCA WebService error", e);
            throw new EjbcaException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }

    @Override
    public List<CertificateWrapper> getLastCertChain(final AuthenticationToken authenticationToken, final String username) throws AuthorizationDeniedException, EjbcaException {
        if (log.isTraceEnabled()) {
            log.trace(">getLastCertChain: "+username);
        }
        final List<CertificateWrapper> retval = new ArrayList<>();
        if (endEntityAccessSession.findUser(authenticationToken, username) != null) { // checks authorization on CA and profiles and view_end_entity
            Collection<CertificateWrapper> certs = certificateStoreSession.findCertificatesByUsername(username);
            if (certs.size() > 0) {
                // The latest certificate will be first
                CertificateWrapper firstcert = certs.iterator().next();
                Certificate lastcert;
                if (firstcert != null) {
                    retval.add(firstcert);
                    lastcert = firstcert.getCertificate();
                    if (log.isDebugEnabled()) {
                        log.debug("Found certificate for user with subjectDN: "+CertTools.getSubjectDN(lastcert)+" and serialNo: "+CertTools.getSerialNumberAsString(lastcert));
                    }
                    // If we added a certificate, we will also append the CA certificate chain
                    boolean selfSigned = false;
                    int iteration = 0; // to control so we don't enter an infinite loop. Max chain length is 10
                    while (!selfSigned && iteration < 10) {
                        iteration++;
                        final String issuerDN = CertTools.getIssuerDN(lastcert);
                        final Collection<Certificate> cacerts = certificateStoreSession.findCertificatesBySubject(issuerDN);
                        if (cacerts == null || cacerts.size() == 0) {
                            log.info("No certificate found for CA with subjectDN: "+issuerDN);
                            break;
                        }
                        for (final Certificate cert : cacerts) {
                            try {
                                lastcert.verify(cert.getPublicKey());
                                // this was the right certificate
                                retval.add(EJBTools.wrap(cert));
                                // To determine if we have found the last certificate or not
                                selfSigned = CertTools.isSelfSigned(cert);
                                // Find the next certificate in the chain now
                                lastcert = cert;
                                break; // Break of iteration over this CAs certs
                            } catch (Exception e) {
                                log.debug("Failed verification when looking for CA certificate, this was not the correct CA certificate. IssuerDN: "+issuerDN+", serno: "+CertTools.getSerialNumberAsString(cert));
                            }
                        }
                    }

                } else {
                    log.debug("Found no certificate (in non null list??) for user "+username);
                }
            } else {
                log.debug("Found no certificate for user "+username);
            }
        } else {
            String msg = intres.getLocalizedMessage("ra.errorentitynotexist", username);
            log.debug(msg);
        }
        if (log.isTraceEnabled()) {
            log.trace("<getLastCertChain: "+username);
        }
        return retval;
    }

    @Override
    public boolean markForRecovery(AuthenticationToken authenticationToken, String username, String newPassword, CertificateWrapper cert, boolean localKeyGeneration) throws AuthorizationDeniedException, ApprovalException,
                                    CADoesntExistsException, WaitingForApprovalException, NoSuchEndEntityException, EndEntityProfileValidationException {
        boolean keyRecoverySuccessful;
        boolean authorized = true;
        // If called from the wrong instance, return to proxybean and try next implementation
        if (endEntityAccessSession.findUser(authenticationToken, username) == null) {
            return false;
        }
        int endEntityProfileId = endEntityAccessSession.findUser(authenticationToken, username).getEndEntityProfileId();
        if (((GlobalConfiguration) globalConfigurationSession.getCachedConfiguration(GlobalConfiguration.GLOBAL_CONFIGURATION_ID)).getEnableEndEntityProfileLimitations()) {
            authorized = authorizationSession.isAuthorized(authenticationToken, AccessRulesConstants.ENDENTITYPROFILEPREFIX + Integer.toString(endEntityProfileId) + AccessRulesConstants.KEYRECOVERY_RIGHTS,
                                                            AccessRulesConstants.REGULAR_RAFUNCTIONALITY + AccessRulesConstants.KEYRECOVERY_RIGHTS);
        }
        if (authorized) {
            try {
                if (!localKeyGeneration) {
                    keyRecoverySuccessful = endEntityManagementSessionLocal.prepareForKeyRecovery(authenticationToken, username, endEntityProfileId, cert.getCertificate());
                } else {
                    // In this case, the users status is set to 'Key Recovery' but not the flag in KeyRecoveryData since
                    // this is stored in another instance database
                    keyRecoverySuccessful = endEntityManagementSessionLocal.prepareForKeyRecoveryInternal(authenticationToken, username, endEntityProfileId, cert.getCertificate());
                }
                if (keyRecoverySuccessful && newPassword != null) {
                    // No approval required, continue by setting a new enrollment code
                    endEntityManagementSessionLocal.setPassword(authenticationToken, username, newPassword);
                }
            } catch (WaitingForApprovalException e) {
                // Set new EE password anyway
                if (newPassword != null) { // Password may null if there is a call from EjbcaWS
                    endEntityManagementSessionLocal.setPassword(authenticationToken, username, newPassword);
                }
                throw e;
            }
            return keyRecoverySuccessful;
        }
        return false;
    }

    @Override
    public boolean editUser(AuthenticationToken authenticationToken, EndEntityInformation endEntityInformation)
            throws AuthorizationDeniedException, EndEntityProfileValidationException,
            WaitingForApprovalException, CADoesntExistsException, ApprovalException,
            CertificateSerialNumberException, IllegalNameException, NoSuchEndEntityException, CustomFieldException {
        if (endEntityAccessSession.findUser(authenticationToken, endEntityInformation.getUsername()) == null) {
            // If called from the wrong instance, return to proxybean and try next implementation
            return false;
        } else {
            endEntityManagementSessionLocal.changeUser(authenticationToken, endEntityInformation, false);
            return true;
        }
    }

    @Override
    public boolean keyRecoveryPossible(final AuthenticationToken authenticationToken, Certificate cert, String username) {
        boolean returnval = true;
        returnval = isAuthorizedNoLogging(authenticationToken, AccessRulesConstants.REGULAR_KEYRECOVERY);
        if (((GlobalConfiguration) globalConfigurationSession.getCachedConfiguration(GlobalConfiguration.GLOBAL_CONFIGURATION_ID)).getEnableEndEntityProfileLimitations()) {
            try {
                EndEntityInformation data = endEntityAccessSession.findUser(authenticationToken, username);
                if (data != null) {
                    int profileid = data.getEndEntityProfileId();
                    returnval = endEntityAuthorization(authenticationToken, profileid, AccessRulesConstants.KEYRECOVERY_RIGHTS, false);
                } else {
                    returnval = false;
                }
            } catch (AuthorizationDeniedException e) {
                log.debug("Administrator: " + authenticationToken + " was not authorized to perform key recovery for end entity: " + username);
                return false;
            }
        }
        return returnval && keyRecoverySessionLocal.existsKeys(EJBTools.wrap(cert)) && !keyRecoverySessionLocal.isUserMarked(username);
    }

    @Override
    public void keyRecoverWS(AuthenticationToken authenticationToken, String username, String certSNinHex, String issuerDN) throws EjbcaException, AuthorizationDeniedException,
                WaitingForApprovalException, ApprovalException, CADoesntExistsException {
        try {
            final boolean usekeyrecovery = ((GlobalConfiguration) globalConfigurationSession.getCachedConfiguration(GlobalConfiguration.GLOBAL_CONFIGURATION_ID)).getEnableKeyRecovery();
            if (!usekeyrecovery) {
                throw new EjbcaException(ErrorCode.KEY_RECOVERY_NOT_AVAILABLE, "Keyrecovery must be enabled in the system configuration in order to execute this command.");

            }
            final EndEntityInformation userdata = endEntityAccessSession.findUser(authenticationToken, username);
            if (userdata == null) {
                log.info(intres.getLocalizedMessage("ra.errorentitynotexist", username));
                final String msg = intres.getLocalizedMessage("ra.errorentitynotexist", username);
                throw new NotFoundException(msg);
            }
            if (keyRecoverySessionLocal.isUserMarked(username)) {
                // User is already marked for recovery.
                return;
            }
            // check CAID
            final int caid = userdata.getCAId();
            caSession.verifyExistenceOfCA(caid);
            if (!authorizationSession.isAuthorizedNoLogging(authenticationToken, StandardRules.CAACCESS.resource() + caid)) {
                final String msg = intres.getLocalizedMessage("authorization.notuathorizedtoresource", StandardRules.CAACCESS.resource() +caid, null);
                throw new AuthorizationDeniedException(msg);
            }

            // find certificate to recover
            final Certificate cert = certificateStoreSession.findCertificateByIssuerAndSerno(issuerDN, new BigInteger(certSNinHex,16));
            if (cert == null) {
                final String msg = intres.getLocalizedMessage("ra.errorfindentitycert", issuerDN, certSNinHex);
                throw new NotFoundException(msg);
            }

            // Do the work, mark user for key recovery
            if (!endEntityManagementSessionLocal.prepareForKeyRecovery(authenticationToken, userdata.getUsername(), userdata.getEndEntityProfileId(), cert)) {
                // Reset user status and throw exception
                endEntityManagementSessionLocal.setUserStatus(authenticationToken, username, userdata.getStatus());
                throw new EjbcaException(ErrorCode.KEY_RECOVERY_NOT_AVAILABLE, "Key recovery data not found for user '" + username + "'");
            }
        } catch (NotFoundException e) {
            log.debug("EJBCA WebService error", e);
            throw e; // extends EjbcaException
        } catch (NoSuchEndEntityException e) {
            throw new NotFoundException(intres.getLocalizedMessage("ra.errorentitynotexist", username));
        }
    }

    /** Help function used to check end entity profile authorization. */
    private boolean endEntityAuthorization(AuthenticationToken admin, int profileid, String rights, boolean log) {
        boolean returnval = false;
        if (log) {
            returnval = authorizationSession.isAuthorized(admin, AccessRulesConstants.ENDENTITYPROFILEPREFIX + Integer.toString(profileid) + rights,
                    AccessRulesConstants.REGULAR_RAFUNCTIONALITY + rights);
        } else {
            returnval = isAuthorizedNoLogging(admin, AccessRulesConstants.ENDENTITYPROFILEPREFIX + Integer.toString(profileid)
                    + rights, AccessRulesConstants.REGULAR_RAFUNCTIONALITY + rights);
        }
        return returnval;
    }

    @Override
    public boolean changeCertificateStatus(final AuthenticationToken authenticationToken, final String fingerprint, final int newStatus, final int newRevocationReason)
            throws ApprovalException, WaitingForApprovalException {
        final CertificateDataWrapper cdw = searchForCertificate(authenticationToken, fingerprint);
        if (cdw!=null) {
            final BigInteger serialNumber = new BigInteger(cdw.getCertificateData().getSerialNumber());
            final String issuerDn = cdw.getCertificateData().getIssuerDN();
            try {
                // This call checks CA authorization, EEP authorization (if enabled) and /ra_functionality/revoke_end_entity
                endEntityManagementSessionLocal.revokeCert(authenticationToken, serialNumber, issuerDn, newRevocationReason);
                return true;
            } catch (AlreadyRevokedException e) {
                // If it is already revoked, great! The client got what the client wanted.. (almost at least, since reason might differ)
                log.info("Client '"+authenticationToken+"' requested status change of when status was already set for certificate '"+fingerprint+"'. Considering operation successful.");
                return true;
            } catch (AuthorizationDeniedException e) {
                log.info("Client '"+authenticationToken+"' requested status change of certificate '"+fingerprint+"' but is not authorized to revoke certificates.");
            } catch (NoSuchEndEntityException e) {
                // The certificate did exist a few lines ago, but must have been removed since then. Treat this like it never existed
                log.info("Client '"+authenticationToken+"' requested status change of certificate '"+fingerprint+"' that does not exist.");
            }
        } else {
            log.info("Client '"+authenticationToken+"' requested status change of certificate '"+fingerprint+"' that does not exist or the client is not authorized to see.");
        }
        return false;
    }

    private GlobalCesecoreConfiguration getGlobalCesecoreConfiguration() {
        return (GlobalCesecoreConfiguration) globalConfigurationSession.getCachedConfiguration(GlobalCesecoreConfiguration.CESECORE_CONFIGURATION_ID);
    }

    @Override
    public ApprovalProfile getApprovalProfileForAction(final AuthenticationToken authenticationToken, final ApprovalRequestType action, final int caId, final int certificateProfileId) throws AuthorizationDeniedException{
        KeyToValueHolder<CAInfo> caInfoHolder = getAuthorizedCAInfos(authenticationToken).get(caId);
        KeyToValueHolder<CertificateProfile> certificateProfileHolder = getAuthorizedCertificateProfiles(authenticationToken).get(certificateProfileId);
        if(caInfoHolder == null){
            throw new AuthorizationDeniedException("Could not get approval profile because " + authenticationToken + " doesn't have access to CA with ID = " + caId);
        }
        if(certificateProfileHolder == null){
            throw new AuthorizationDeniedException("Could not get approval profile because " + authenticationToken + " doesn't have access to certificate profile with ID = " + certificateProfileId);
        }
        return approvalProfileSession.getApprovalProfileForAction(action, caInfoHolder.getValue(), certificateProfileHolder.getValue());
    }

    @Override
    public byte[] cmpDispatch(final AuthenticationToken authenticationToken, final byte[] pkiMessageBytes, final String cmpConfigurationAlias) throws NoSuchAliasException {
        return cmpMessageDispatcherSession.dispatchRequest(authenticationToken, pkiMessageBytes, cmpConfigurationAlias);
    }

    @Override
    public byte[] estDispatch(String operation, String alias, X509Certificate cert, String username, String password, byte[] requestBody)
            throws NoSuchAliasException, CADoesntExistsException, CertificateCreateException, CertificateRenewalException, AuthenticationFailedException {
        // throws UnsupportedOperationException if EST is not available (Community);
        return estOperationsSessionLocal.dispatchRequest(operation, alias, cert, username, password, requestBody);
    }

}
