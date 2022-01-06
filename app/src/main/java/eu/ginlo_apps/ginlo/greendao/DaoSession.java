// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.greendao;

import org.greenrobot.greendao.AbstractDao;
import org.greenrobot.greendao.AbstractDaoSession;
import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.identityscope.IdentityScopeType;
import org.greenrobot.greendao.internal.DaoConfig;

import java.util.Map;

import eu.ginlo_apps.ginlo.greendao.Account;
import eu.ginlo_apps.ginlo.greendao.AccountDao;
import eu.ginlo_apps.ginlo.greendao.Channel;
import eu.ginlo_apps.ginlo.greendao.ChannelCategory;
import eu.ginlo_apps.ginlo.greendao.ChannelCategoryDao;
import eu.ginlo_apps.ginlo.greendao.ChannelDao;
import eu.ginlo_apps.ginlo.greendao.Chat;
import eu.ginlo_apps.ginlo.greendao.ChatDao;
import eu.ginlo_apps.ginlo.greendao.CompanyContact;
import eu.ginlo_apps.ginlo.greendao.CompanyContactDao;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.greendao.ContactDao;
import eu.ginlo_apps.ginlo.greendao.Device;
import eu.ginlo_apps.ginlo.greendao.DeviceDao;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.greendao.MessageDao;
import eu.ginlo_apps.ginlo.greendao.NewDestructionDate;
import eu.ginlo_apps.ginlo.greendao.NewDestructionDateDao;
import eu.ginlo_apps.ginlo.greendao.Notification;
import eu.ginlo_apps.ginlo.greendao.NotificationDao;
import eu.ginlo_apps.ginlo.greendao.Preference;
import eu.ginlo_apps.ginlo.greendao.PreferenceDao;
import eu.ginlo_apps.ginlo.greendao.Product;
import eu.ginlo_apps.ginlo.greendao.ProductDao;
import eu.ginlo_apps.ginlo.greendao.StatusText;
import eu.ginlo_apps.ginlo.greendao.StatusTextDao;

/**
 * {@inheritDoc}
 *
 * @see AbstractDaoSession
 */
public class DaoSession
        extends AbstractDaoSession {

    private final DaoConfig preferenceDaoConfig;

    private final DaoConfig accountDaoConfig;

    private final DaoConfig contactDaoConfig;

    private final DaoConfig companyContactDaoConfig;

    private final DaoConfig messageDaoConfig;

    private final DaoConfig chatDaoConfig;

    private final DaoConfig productDaoConfig;

    private final DaoConfig statusTextDaoConfig;

    private final DaoConfig channelDaoConfig;

    private final DaoConfig newDestructionDateDaoConfig;

    private final DaoConfig channelCategoryDaoConfig;

    private final DaoConfig deviceDaoConfig;

    private final DaoConfig notificationDaoConfig;

    private final PreferenceDao preferenceDao;

    private final eu.ginlo_apps.ginlo.greendao.AccountDao accountDao;

    private final eu.ginlo_apps.ginlo.greendao.ContactDao contactDao;

    private final CompanyContactDao companyContactDao;

    private final MessageDao messageDao;

    private final eu.ginlo_apps.ginlo.greendao.ChatDao chatDao;

    private final ProductDao productDao;

    private final StatusTextDao statusTextDao;

    private final eu.ginlo_apps.ginlo.greendao.ChannelDao channelDao;

    private final NewDestructionDateDao newDestructionDateDao;

    private final ChannelCategoryDao channelCategoryDao;

    private final eu.ginlo_apps.ginlo.greendao.DeviceDao deviceDao;

    private final NotificationDao notificationDao;

    public DaoSession(Database db,
                      IdentityScopeType type,
                      Map<Class<? extends AbstractDao<?, ?>>, DaoConfig> daoConfigMap) {
        super(db);

        preferenceDaoConfig = daoConfigMap.get(PreferenceDao.class).clone();
        preferenceDaoConfig.initIdentityScope(type);

        accountDaoConfig = daoConfigMap.get(eu.ginlo_apps.ginlo.greendao.AccountDao.class).clone();
        accountDaoConfig.initIdentityScope(type);

        contactDaoConfig = daoConfigMap.get(eu.ginlo_apps.ginlo.greendao.ContactDao.class).clone();
        contactDaoConfig.initIdentityScope(type);

        companyContactDaoConfig = daoConfigMap.get(CompanyContactDao.class).clone();
        companyContactDaoConfig.initIdentityScope(type);

        messageDaoConfig = daoConfigMap.get(MessageDao.class).clone();
        messageDaoConfig.initIdentityScope(type);

        chatDaoConfig = daoConfigMap.get(eu.ginlo_apps.ginlo.greendao.ChatDao.class).clone();
        chatDaoConfig.initIdentityScope(type);

        productDaoConfig = daoConfigMap.get(ProductDao.class).clone();
        productDaoConfig.initIdentityScope(type);

        statusTextDaoConfig = daoConfigMap.get(StatusTextDao.class).clone();
        statusTextDaoConfig.initIdentityScope(type);

        channelDaoConfig = daoConfigMap.get(eu.ginlo_apps.ginlo.greendao.ChannelDao.class).clone();
        channelDaoConfig.initIdentityScope(type);

        newDestructionDateDaoConfig = daoConfigMap.get(NewDestructionDateDao.class).clone();
        newDestructionDateDaoConfig.initIdentityScope(type);

        channelCategoryDaoConfig = daoConfigMap.get(ChannelCategoryDao.class).clone();
        channelCategoryDaoConfig.initIdentityScope(type);

        deviceDaoConfig = daoConfigMap.get(eu.ginlo_apps.ginlo.greendao.DeviceDao.class).clone();
        deviceDaoConfig.initIdentityScope(type);

        notificationDaoConfig = daoConfigMap.get(NotificationDao.class).clone();
        notificationDaoConfig.initIdentityScope(type);

        preferenceDao = new PreferenceDao(preferenceDaoConfig, this);
        accountDao = new eu.ginlo_apps.ginlo.greendao.AccountDao(accountDaoConfig, this);
        contactDao = new eu.ginlo_apps.ginlo.greendao.ContactDao(contactDaoConfig, this);
        companyContactDao = new CompanyContactDao(companyContactDaoConfig, this);
        messageDao = new MessageDao(messageDaoConfig, this);
        chatDao = new eu.ginlo_apps.ginlo.greendao.ChatDao(chatDaoConfig, this);
        productDao = new ProductDao(productDaoConfig, this);
        statusTextDao = new StatusTextDao(statusTextDaoConfig, this);
        channelDao = new eu.ginlo_apps.ginlo.greendao.ChannelDao(channelDaoConfig, this);
        newDestructionDateDao = new NewDestructionDateDao(newDestructionDateDaoConfig, this);
        channelCategoryDao = new ChannelCategoryDao(channelCategoryDaoConfig, this);
        deviceDao = new eu.ginlo_apps.ginlo.greendao.DeviceDao(deviceDaoConfig, this);
        notificationDao = new NotificationDao(notificationDaoConfig, this);

        registerDao(Preference.class, preferenceDao);
        registerDao(Account.class, accountDao);
        registerDao(Contact.class, contactDao);
        registerDao(CompanyContact.class, companyContactDao);
        registerDao(Message.class, messageDao);
        registerDao(Chat.class, chatDao);
        registerDao(Product.class, productDao);
        registerDao(StatusText.class, statusTextDao);
        registerDao(Channel.class, channelDao);
        registerDao(NewDestructionDate.class, newDestructionDateDao);
        registerDao(ChannelCategory.class, channelCategoryDao);
        registerDao(Device.class, deviceDao);
        registerDao(Notification.class, notificationDao);
    }

    public void clear() {
        preferenceDaoConfig.getIdentityScope().clear();
        accountDaoConfig.getIdentityScope().clear();
        contactDaoConfig.getIdentityScope().clear();
        companyContactDaoConfig.getIdentityScope().clear();
        messageDaoConfig.getIdentityScope().clear();
        chatDaoConfig.getIdentityScope().clear();
        productDaoConfig.getIdentityScope().clear();
        statusTextDaoConfig.getIdentityScope().clear();
        channelDaoConfig.getIdentityScope().clear();
        newDestructionDateDaoConfig.getIdentityScope().clear();
        channelCategoryDaoConfig.getIdentityScope().clear();
        notificationDaoConfig.getIdentityScope().clear();
    }

    public PreferenceDao getPreferenceDao() {
        return preferenceDao;
    }

    public AccountDao getAccountDao() {
        return accountDao;
    }

    public ContactDao getContactDao() {
        return contactDao;
    }

    public CompanyContactDao getCompanyContactDao() {
        return companyContactDao;
    }

    public MessageDao getMessageDao() {
        return messageDao;
    }

    public ChatDao getChatDao() {
        return chatDao;
    }

    public ProductDao getProductDao() {
        return productDao;
    }

    public StatusTextDao getStatusTextDao() {
        return statusTextDao;
    }

    public ChannelDao getChannelDao() {
        return channelDao;
    }

    public NewDestructionDateDao getNewDestructionDateDao() {
        return newDestructionDateDao;
    }

    public ChannelCategoryDao getChannelCategoryDao() {
        return channelCategoryDao;
    }

    /**
     * getDeviceDao
     *
     * @return
     */
    public DeviceDao getDeviceDao() {
        return deviceDao;
    }

    /**
     * getNotificationDao
     *
     * @return
     */
    public NotificationDao getNotificationDao() {
        return notificationDao;
    }
}
