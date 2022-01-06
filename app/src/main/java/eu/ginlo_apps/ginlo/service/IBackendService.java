// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.service;

import androidx.annotation.NonNull;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import eu.ginlo_apps.ginlo.billing.GinloPurchaseImpl;
import eu.ginlo_apps.ginlo.concurrent.task.HttpBaseTask;
import eu.ginlo_apps.ginlo.model.backend.BackendResponse;
import org.json.JSONArray;

public interface IBackendService {
    void elevateRights(final String deviceGuid,
                       final String accountGuid,
                       final String passToken);

    void createAccountEx(final String dataJson,
                         final String phoneNumber,
                         final String language,
                         final String cockpitToken,
                         final String cockpitData,
                         final OnBackendResponseListener listener);

    void requestCoupling(final String accountGuid,
                         final String transactionID,
                         final String publicKey,
                         final String encryptionVerify,
                         final String reqType,
                         final String appData,
                         final String signature,
                         final OnBackendResponseListener listener);

    void getCouplingResponse(final String accountGuid,
                             final String transactionID,
                             final OnBackendResponseListener listener);

    void isConfirmationValid(final String confirmCode,
                             final OnBackendResponseListener listener);

    void validateMail(final String email,
                      final boolean isAccountConfirmed,
                      final OnBackendResponseListener listener);

    void createDevice(final String accountGuid,
                      final String devicePassToken,
                      final String deviceJson,
                      final String phone,
                      final OnBackendResponseListener listener);

    void createAdditionalDevice(final String accountGuid,
                                final String transId,
                                final String deviceJsonString,
                                final OnBackendResponseListener listener);

    void confirmAccount(final String confirmationCode,
                        final OnBackendResponseListener listener);

    void resetBadge(final OnBackendResponseListener listener);

    void deleteAccount(final OnBackendResponseListener listener);

    void isMessageSend(final String senderId,
                       final OnBackendResponseListener listener);

    void sendPrivateMessage(final String messageJson,
                            final OnBackendResponseListener listener,
                            final String requestId);

    void sendTimedPrivateMessage(final String messageJson,
                                 final OnBackendResponseListener listener,
                                 final String requestId,
                                 final String date);

    void sendPrivateInternalMessage(final String messageJson,
                                    final OnBackendResponseListener listener,
                                    final String requestId);

    /**
     * Versenden mehrerer Interner Nachrichten.
     *
     * @param messagesJson JSON-Array mit JSON-Objekt PrivateInternalMessage
     */
    void sendPrivateInternalMessages(final String messagesJson,
                                     final OnBackendResponseListener listener);

    void sendGroupMessage(final String messageJson,
                          final OnBackendResponseListener listener,
                          final String requestId);

    void sendTimedGroupMessage(final String messageJson,
                               final OnBackendResponseListener listener,
                               final String requestId,
                               final String date);

    void getTenants(final OnBackendResponseListener listener);

    void getNewMessages(final OnBackendResponseListener listener,
                        final boolean useLazyMsgService);

    void getNewMessagesFromBackground(final OnBackendResponseListener listener);

    void getMessages(final String guids,
                     final OnBackendResponseListener listener);

    void getPrioMessages(final OnBackendResponseListener listener);

    void getTimedMessageGuids(final OnBackendResponseListener listener);

    void getTimedMessages(final String guids, final OnBackendResponseListener listener);

    void getKnownAccounts(final JsonArray data,
                          final String salt,
                          final String tenant,
                          final String searchMode,
                          final OnBackendResponseListener listener);

    void getAccountInfoAnonymous(final String searchText,
                                 final String searchMode,
                                 final OnBackendResponseListener listener);

    void getAccountInfo(final String accountGuid,
                        final int withProfileInfo,
                        final boolean withMandant,
                        final boolean checkReadonly,
                        final OnBackendResponseListener listener);

    void getAccountImage(final String accountGuid,
                         final OnBackendResponseListener listener);

    /**
     * Abfragen der Account-Informationen im BatchModus. Es werden nur die
     * öffentlichen Daten aus der Tabelle msg_public_account zurückgegeben. Dies
     * ist insbesondere nur der PublicKey des Accounts. Die Telefonnummer wird
     * explizit NICHT zurückgegeben. Für nicht vorhandene Accounts wird KEINE
     * Exception zurück gegeben, sondern es wird keine Antwort für diesen Eintrag
     * zurückgegeben Server Responce: JSON-Array mit Verkürzte
     * Accountinformationen: Bsp.:
     * [{"Account":{"guid":"0:{1234}","publicKey":"<RSA..."}}]
     *
     * @param accountGuids Comma separated list of account guid's
     * @param withTenant   tenant related
     * @param listener     response listener
     */
    void getAccountInfoBatch(final String accountGuids,
                             final boolean withProfileInfo,
                             final boolean withTenant,
                             final OnBackendResponseListener listener);

    void getAttachment(final String attachmentGuid,
                       final OnBackendResponseListener listener,
                       final HttpBaseTask.OnConnectionDataUpdatedListener onConnectionDataUpdatedListener);

    void getBackgroundAccessToken(final OnBackendResponseListener listener);

    void setDeviceData(final String dataJson,
                       final OnBackendResponseListener listener);

    void createGroup(final String dataJson,
                     final String groupInvMessagesAsJson,
                     final String adminGuids,
                     final String nickname,
                     final OnBackendResponseListener listener);

    void getRoom(final String chatRoomGuid,
                 final OnBackendResponseListener listener,
                 final boolean checkReadonly);

    void getRoomMemberInfo(final String chatRoomGuid,
                           final OnBackendResponseListener listener);

    void updateGroup(final String groupGuid,
                     final String newMembers,
                     final String removedMembers,
                     final String newAdmins,
                     final String removedAdmins,
                     final String data,
                     final String keyIv,
                     final String nickname,
                     final OnBackendResponseListener listener);

    void getCurrentRoomInfo(final OnBackendResponseListener listener);

    void setGroupInfo(final String groupGuid,
                      final String data,
                      final String keyIv,
                      final OnBackendResponseListener listener);

    void removeRoom(final String chatRoomGuid,
                    final OnBackendResponseListener listener);

    void removeTimedMessage(final String messageGuid,
                            final OnBackendResponseListener listener);

    void removeTimedMessageBatch(final String messageGuids,
                                 final OnBackendResponseListener listener);

    void removeFromRoom(final String chatRoomGuid,
                        final String removeAccountGuid,
                        final String nickname,
                        final OnBackendResponseListener listener);

    void acceptRoomInvitation(final String chatRoomGuid,
                              final String nickname,
                              final OnBackendResponseListener listener);

    void declineRoomInvitation(final String chatRoomGuid,
                               final String nickname,
                               final OnBackendResponseListener listener);

    void getCompany(final OnBackendResponseListener listener);

    void getCompanyLayout(final OnBackendResponseListener listener);

    void getCompanyLogo(final OnBackendResponseListener listener);

    void requestCompanyRecoveryKey(final String data, final OnBackendResponseListener listener);

    void getSimsmeRecoveryPublicKey(final OnBackendResponseListener listener);

    void requestSimsmeRecoveryKey(final OnBackendResponseListener listener,
                                  final String transId,
                                  final String recoveryToken,
                                  final String recoveryChannel,
                                  final String sig
    );

    void getConfigVersions(final OnBackendResponseListener listener);

    void getConfiguration(final OnBackendResponseListener listener);

    void setMessageState(final JsonArray guids,
                         final String state,
                         final OnBackendResponseListener listener,
                         final boolean useBackgroundEndpoint);

    void setNotificationSound(final String dataJson,
                              final OnBackendResponseListener listener);

    void setNotification(final boolean enabled,
                         final OnBackendResponseListener listener);

    void setGroupNotification(final boolean enabled,
                              final OnBackendResponseListener listener);

    void setServiceNotification(final boolean enabled,
                                final OnBackendResponseListener listener);

    void setServiceNotificationForService(final String guid,
                                          final boolean enabled,
                                          final OnBackendResponseListener listener);

    void setChannelNotification(final boolean enabled,
                                final OnBackendResponseListener listener);

    void setBlocked(final String guid,
                    final boolean isBlocked,
                    final OnBackendResponseListener listener);

    void getBlocked(final OnBackendResponseListener listener);

    void registerPayment(final GinloPurchaseImpl ginloPurchase,
                         final OnBackendResponseListener listener);

    void registerVoucher(final String voucherCode,
                         final OnBackendResponseListener listener);

    void requestConfirmationMail(final String eMailAddress,
                                 final boolean forceCreation,
                                 final OnBackendResponseListener listener);

    void confirmConfirmationMail(final String confirmCode,
                                 final OnBackendResponseListener listener);

    void requestConfirmPhone(final String phone,
                             final boolean forceCreation,
                             final OnBackendResponseListener listener);

    void confirmConfirmPhone(final String confirmCode,
                             final OnBackendResponseListener listener);

    void removeConfirmedMail(final String emailAddress,
                             final OnBackendResponseListener listener);

    void removeConfirmedPhone(final String phoneNumber,
                              final OnBackendResponseListener listener);

    void setAddressInformation(final String dataJson,
                               final OnBackendResponseListener listener);

    void setAutoGeneratedMessages(final String dataJson,
                                  final OnBackendResponseListener listener);

    void getAutoGeneratedMessages(final OnBackendResponseListener listener);

    void getAddressInformation(final OnBackendResponseListener listener);

    void getAddressInformationBatch(final String guids,
                                    final OnBackendResponseListener listener);

    void getPurchasedProducts(final OnBackendResponseListener listener);

    void getProducts(final OnBackendResponseListener listener);

    void getChannelList(final OnBackendResponseListener listener);

    void getChannelDetails(final String guid,
                           final OnBackendResponseListener listener);

    void getChannelDetailsBatch(final String guids,
                                final OnBackendResponseListener listener);

    void getChannelCategories(final OnBackendResponseListener listener);

    void setChannelNotificationForChannel(final String guid,
                                          final boolean isEnabled,
                                          final OnBackendResponseListener listener);

    void setFollowedChannels(final String followedChannelsJsonArray,
                             final OnBackendResponseListener listener);

    void setFollowedServices(final String followedServicesJsonArray,
                             final OnBackendResponseListener listener);

    void subscribeService(final String guid,
                          final String filter,
                          final OnBackendResponseListener listener);

    void cancelServiceSubscription(final String guid,
                                   final OnBackendResponseListener listener);

    void subscribeChannel(final String guid,
                          final String filter,
                          final OnBackendResponseListener listener);

    void cancelChannelSubscription(final String guid,
                                   final OnBackendResponseListener listener);

    void getChannelAsset(final String guid,
                         final String type,
                         final String res,
                         final OnBackendResponseListener listener);

    boolean isConnected();

    boolean isConnectedViaWLAN();

    void setUseAsyncConnections(final boolean useAsyncConnections);

    void trackEvents(final String trackingGuid,
                     final JSONArray data,
                     final OnBackendResponseListener listener);

    void setProfileInfo(final String nickName,
                        final String status,
                        final String image,
                        final String informAccountGuids,
                        final String oooStatus,
                        final OnBackendResponseListener listener);

    void setDeviceName(final String guid,
                       final String deviceNameEncoded,
                       final OnBackendResponseListener listener);

    void createBackupPassToken(final OnBackendResponseListener listener);

    void resetPurchaseToken(final String purchaseTokens, final OnBackendResponseListener listener);

    void hasCompanyManagement(final OnBackendResponseListener listener);

    void declineCompanyManagement(final OnBackendResponseListener listener);

    void acceptCompanyManagement(final OnBackendResponseListener listener);

    void getCompanyMdmConfig(final OnBackendResponseListener listener);

    void getConfirmedIdentities(final OnBackendResponseListener listener);

    void registerTestVoucher(final OnBackendResponseListener listener);

    void getTestVoucherInfo(final OnBackendResponseListener listener);

    void getDevices(final OnBackendResponseListener listener);

    void deleteDevice(final String guid, final OnBackendResponseListener listener);

    void listPrivateIndexEntries(final String ifModifiedSince, final OnBackendResponseListener listener);

    void getPrivateIndexEntries(@NonNull final String guids,
                                final OnBackendResponseListener listener);

    void insUpdPrivateIndexEntry(@NonNull final JsonObject entry, final OnBackendResponseListener listener);

    void initialiseCoupling(final String transactionID,
                            final String timestamp,
                            final String encTan,
                            final String appData,
                            final String signature,
                            final OnBackendResponseListener listener);

    void cancelCoupling(final String transactionID,
                        final OnBackendResponseListener listener);

    void responseCoupling(
            final String transactionID,
            final String device,
            final String devKeySig,
            final String kek,
            final String kekIv,
            final String encSyncData,
            final String appData,
            final String sig,
            final OnBackendResponseListener listener);

    void getCouplingRequest(final String transactionID,
                            final OnBackendResponseListener listener);

    void setChatDeleted(final String guid, final OnBackendResponseListener listener);

    void requestEncryptionInfo(final OnBackendResponseListener listener);

    void deletePrivateIndexEntries(final String guids,
                                   final OnBackendResponseListener listener);

    void setSilentSingleChat(final String accountGuid,
                             final String date,
                             final OnBackendResponseListener listener);

    void setSilentGroupChat(final String roomGuid,
                            final String date,
                            final OnBackendResponseListener listener);

    void setIsWriting(final String guid,
                      final OnBackendResponseListener listener);

    void resetIsWriting(final String guid,
                        final OnBackendResponseListener listener);

    void getOnlineState(final String guid,
                        final String lastKnownState,
                        final OnBackendResponseListener listener);

    void getOnlineStateBatch(final String guids,
                             final OnBackendResponseListener listener);

    void setPublicOnlineState(final boolean state,
                              final OnBackendResponseListener listener);

    void listCompanyIndex(final String ifModifiedSince, final OnBackendResponseListener listener);

    void getCompanyIndexEntries(final String guids, final OnBackendResponseListener listener);

    interface OnBackendResponseListener {
        void onBackendResponse(BackendResponse response);
    }
}
