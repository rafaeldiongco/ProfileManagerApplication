package ph.edu.dlsu.lbycpob.profilemanagerapplication.service;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ph.edu.dlsu.lbycpob.profilemanagerapplication.model.Friend;
import ph.edu.dlsu.lbycpob.profilemanagerapplication.model.Profile;
import ph.edu.dlsu.lbycpob.profilemanagerapplication.repository.FriendRepository;
import ph.edu.dlsu.lbycpob.profilemanagerapplication.repository.ProfileRepository;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

@Service
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final FriendRepository friendRepository;
    private final PictureStorageService pictureStorageService;

    public ProfileService(ProfileRepository profileRepository,
                          FriendRepository friendRepository,
                          PictureStorageService pictureStorageService) {
        this.profileRepository = profileRepository;
        this.friendRepository = friendRepository;
        this.pictureStorageService = pictureStorageService;
    }

    public List<Profile> listProfiles() {
        return profileRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
    }

    public Profile getProfile(UUID id) {
        return profileRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Profile not found."));
    }

    public Profile lookupFirstMatch(String query) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Search query must not be blank.");
        }
        return profileRepository.findByNameContainingIgnoreCaseOrderByNameAsc(trimmed).stream()
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("No profile matches \"" + trimmed + "\"."));
    }

    public List<Profile> getFriendsOf(UUID id) {
        return friendRepository.findByProfileId(id).stream()
                .map(f -> profileRepository.findById(f.getFriendId()).orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional
    public Profile createProfile(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Name must not be blank.");
        }
        if (profileRepository.existsByNameIgnoreCase(trimmed)) {
            throw new IllegalStateException("A profile named \"" + trimmed + "\" already exists.");
        }
        return profileRepository.save(Profile.builder().name(trimmed).build());
    }

    @Transactional
    public void deleteProfile(UUID id) {
        if (!profileRepository.existsById(id)) {
            throw new NoSuchElementException("Profile not found.");
        }
        // Friend rows on either side are removed by the DB's ON DELETE CASCADE.
        profileRepository.deleteById(id);
    }

    @Transactional
    public void updateStatus(UUID id, String status) {
        Profile profile = getProfile(id);
        String trimmed = status == null ? "" : status.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Status must not be blank.");
        }
        profile.setStatus(trimmed);
        profileRepository.save(profile);
    }

    @Transactional
    public void updateQuote(UUID id, String quote) {
        Profile profile = getProfile(id);
        String trimmed = quote == null ? "" : quote.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Quote must not be blank.");
        }
        profile.setQuote(trimmed);
        profileRepository.save(profile);
    }

    @Transactional
    public void updatePictureUrl(UUID id, String pictureUrl) {
        String trimmed = pictureUrl == null ? "" : pictureUrl.trim();
        if (!trimmed.startsWith("https://")) {
            throw new IllegalArgumentException("Picture URL must start with https://");
        }
        Profile profile = getProfile(id);
        profile.setPicture(trimmed);
        profileRepository.save(profile);
    }

    @Transactional
    public String updatePictureFromUpload(UUID id, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file was uploaded.");
        }
        Profile profile = getProfile(id);
        String url = pictureStorageService.storeAvatar(id, file);
        profile.setPicture(url);
        profileRepository.save(profile);
        return url;
    }

    @Transactional
    public String addFriend(UUID id, String friendName) {
        Profile profile = getProfile(id);
        Profile friend = resolveByName(friendName);

        if (friend.getId().equals(profile.getId())) {
            throw new IllegalArgumentException("A profile cannot be friends with itself.");
        }
        if (friendRepository.findByProfileIdAndFriendId(profile.getId(), friend.getId()).isPresent()) {
            throw new IllegalStateException(friend.getName() + " is already a friend.");
        }

        // Directional rows on both sides, so the friendship shows up for either profile.
        friendRepository.save(Friend.builder().profileId(profile.getId()).friendId(friend.getId()).build());
        friendRepository.save(Friend.builder().profileId(friend.getId()).friendId(profile.getId()).build());

        return friend.getName();
    }

    @Transactional
    public String removeFriend(UUID id, String friendName) {
        Profile profile = getProfile(id);
        Profile friend = resolveByName(friendName);

        boolean existed = friendRepository.findByProfileIdAndFriendId(profile.getId(), friend.getId()).isPresent();
        if (!existed) {
            throw new NoSuchElementException(friend.getName() + " is not currently a friend.");
        }

        friendRepository.deleteByProfileIdAndFriendId(profile.getId(), friend.getId());
        friendRepository.deleteByProfileIdAndFriendId(friend.getId(), profile.getId());

        return friend.getName();
    }

    private Profile resolveByName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Friend name must not be blank.");
        }
        return profileRepository.findByNameIgnoreCase(trimmed)
                .orElseThrow(() -> new NoSuchElementException("No profile named \"" + trimmed + "\"."));
    }
}
