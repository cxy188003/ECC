package com.ecc.service.peer;

import com.ecc.domain.peer.Peer;
import com.ecc.domain.security.KeyStorage;
import com.ecc.service.block.BlockService;
import com.ecc.service.contract.ContractService;
import com.ecc.service.transaction.TransactionService;
import com.ecc.service.transfer.TransferService;
import com.ecc.util.converter.DateUtil;
import com.ecc.util.crypto.AesUtil;
import com.ecc.util.crypto.RsaUtil;
import com.ecc.util.system.NetworkUtil;
import com.ecc.web.api.UserServiceApi;
import com.ecc.web.exceptions.UserException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Service;

import java.security.KeyException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.ecc.constants.ApplicationConstants.SERVER_PUBLIC_KEY;

@Service
public class PeerService {
    @Autowired
    BlockService blockService;
    @Autowired
    ContractService contractService;
    @Autowired
    TransactionService transactionService;
    @Autowired
    TransferService transferService;
    @Autowired
    DiscoveryClient discoveryClient;

    @Autowired
    UserServiceApi userServiceApi;

    public Peer register(String email, String channel, String level, String dir) throws Exception {
        RsaUtil.generateKeyPair(email);
        KeyStorage keyStorage = RsaUtil.loadKeyPair(email);

        Peer.getPeer().setEmail(email);
        Peer.getPeer().setChannel(channel);
        Peer.getPeer().setLevel(level);
        Peer.getPeer().setIp(NetworkUtil.getLocalAddress());
        Peer.getPeer().setPort(29626);
        Peer.getPeer().setDir(dir);
        Peer.getPeer().setPublicKey(RsaUtil.getKeyInString(keyStorage.getPublicKey()));
        Peer.getPeer().setRegDate(DateUtil.getDate());

        Peer peer = userServiceApi.register(Peer.getPeer());

        if (peer != null) {
            Peer.getPeer().setId(peer.getId());
            Peer.getPeer().setEmail(peer.getEmail());
            Peer.getPeer().setChannel(peer.getChannel());
            Peer.getPeer().setLevel(peer.getLevel());
            Peer.getPeer().setIp(NetworkUtil.getLocalAddress());
            Peer.getPeer().setPort(29626);
            Peer.getPeer().setDir(dir);
            Peer.getPeer().setPublicKey(peer.getPublicKey());
            Peer.getPeer().setRegDate(peer.getRegDate());
            return peer;
        }
        throw new Exception("Email already been registered!");
    }

    public Peer login(String email, String dir) throws Exception {
        if (RsaUtil.loadKeyPair(email).getPrivateKey() != null) {
            HashMap<String, String> params = userServiceApi.getRandomValue(email);
            String aesKey = RsaUtil.decrypt(params.get("encryptedAesKey"), RsaUtil.loadKeyPair(email).getPrivateKey());
            String aesDecryptValue = AesUtil.decrypt(aesKey, params.get("encryptedData"));
            String randomValue = aesDecryptValue.split("@")[0];
            String signedValue = aesDecryptValue.split("@")[1];

            if (RsaUtil.verify(SERVER_PUBLIC_KEY, randomValue, signedValue)) {
                String verifyValue = randomValue + "@" + RsaUtil.sign(randomValue, RsaUtil.loadKeyPair(email).getPrivateKey());
                verifyValue = AesUtil.encrypt(aesKey, verifyValue);

                //todo: SENT TO SERVER to receive feedback
                Peer newPeer = userServiceApi.returnVerifiedPeer(verifyValue, email);
                Peer.getPeer().setId(newPeer.getId());
                Peer.getPeer().setEmail(newPeer.getEmail());
                Peer.getPeer().setChannel(newPeer.getChannel());
                Peer.getPeer().setLevel(newPeer.getLevel());
                Peer.getPeer().setIp(NetworkUtil.getLocalAddress());
                Peer.getPeer().setPort(29626);
                Peer.getPeer().setDir(dir);
                Peer.getPeer().setPublicKey(newPeer.getPublicKey());
                Peer.getPeer().setRegDate(newPeer.getRegDate());
                Peer.getPeer().setSecretKey(aesKey);

                //todo: update peer current statues
                userServiceApi.login(Peer.getPeer());
                return Peer.getPeer();
            }
            throw new Exception("Random value verify failed!");
        }
        throw new Exception("Cannot locate privateKey file!");
    }

    public List<String> getPeerList(int maxPeers) {
        List<ServiceInstance> instances = discoveryClient.getInstances("peer".toUpperCase());
        List<String> tempList = new ArrayList<>();
        List<String> peerList = new ArrayList<>();

        for (ServiceInstance instance : instances) {
            String email = userServiceApi.getPeer("", instance.getHost()).getEmail();
            tempList.add(email);
        }

        if (maxPeers > tempList.size()) {
            peerList.addAll(tempList);
            for (int i = 0; i < maxPeers - tempList.size(); i++) {
                peerList.add(tempList.get(0));
            }
        } else {
            peerList.addAll(tempList);
        }

        return peerList;
    }
}
