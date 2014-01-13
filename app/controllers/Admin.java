package controllers;

import static play.libs.Json.toJson;

import java.net.URL;
import java.util.*;

import models.*;

import models.utils.Mail;
import com.fasterxml.jackson.databind.JsonNode;

import org.pegdown.PegDownProcessor;
import play.Configuration;
import play.i18n.Messages;
import play.mvc.Controller;
import play.mvc.Result;
import securesocial.core.Identity;
import securesocial.core.java.SecureSocial;

@SecureSocial.SecuredAction(ajaxCall = true)
public class Admin extends Controller {

    public static User getLoggedUser() {
        Identity socialUser = (Identity) ctx().args.get(SecureSocial.USER_KEY);
        User user = User.findByExternalId(socialUser.identityId().userId(), socialUser.identityId().providerId());
        return user;
    }

    public static Result editProfil(Long id) {
        User userRequest = getLoggedUser();
        if (!userRequest.admin) {
            return forbidden();
        }
        User userToEdit = User.findById(id);

        JsonNode node = request().body().asJson();

        if (node != null && node.get("description") != null && !node.get("description").equals("null")) {
            String description = node.get("description").asText();
            userToEdit.description = description;
            userToEdit.save();
            return ok();
        }  else {
            Map<String, List<String>> errors = new HashMap<String, List<String>>();
            errors.put("descriptionE", Collections.singletonList(Messages.get("error.required")));
            return badRequest(toJson(errors));
        }


    }

    public static Result deleteCompte(Long id) {
        User userRequest = getLoggedUser();
        if (!userRequest.admin) {
            return forbidden();
        }

        User userToDelete = User.findById(id);

        if (userToDelete.admin) {
            return badRequest();
        }
        // has talks ?
        List<Talk> talks = Talk.findBySpeaker(userToDelete);
        for (Talk talk : talks) {
            talk.statusTalk = StatusTalk.REJETE;
            talk.speaker = null;
            talk.save();
        }

        //has commentaires ?
        List<Comment> comments = Comment.findByAuthor(userToDelete);
        for (Comment comment : comments) {

            comment.author = null;
            comment.save();
        }


        userToDelete.delete();

        return ok();
    }

    public static Result submitUsers() {
        User userRequest = getLoggedUser();
        if (!userRequest.admin) {
            return forbidden();
        }
        JsonNode node = request().body().asJson();
        Iterator<Map.Entry<String, JsonNode>> iteratorMails = node.fields();
        while (iteratorMails.hasNext()) {
            Map.Entry<String, JsonNode> entry = iteratorMails.next();
            String mail = entry.getKey();
            boolean admin = entry.getValue().asBoolean();
            User user = User.findByEmail(mail);
            if (admin) {
                if (user != null && !user.admin) {
                    user.admin = true;
                    user.save();
                }
            } else {
                if (user != null && user.admin) {
                    user.admin = false;
                    user.save();
                }
            }
        }
        return ok();
    }

    public static class ResultVote {

        public ResultVote(VoteStatusEnum status) {
            this.status = status;
        }

        public VoteStatusEnum status;
    }

    public static Result getVoteStatus() {
        User user = getLoggedUser();
        if (!user.admin) {
            return forbidden();
        }
        return ok(toJson(new ResultVote(VoteStatus.getVoteStatus())));
    }

    public static Result changeVoteStatus(String newStatus) {
        User user = getLoggedUser();
        if (!user.admin) {
            return forbidden();
        }
        VoteStatus.changeVoteStatus(VoteStatusEnum.valueOf(newStatus));
        return ok();
    }

    public static Result mailing(String status) {
        StatusTalk statusTalk = StatusTalk.fromValue(status);

        JsonNode body = request().body().asJson();
        String subjet = body.get("subject").asText();
        String mailMarkdown = body.get("mail").asText();

        PegDownProcessor pegDownProcessor = new PegDownProcessor();
        String mailHtml = pegDownProcessor.markdownToHtml(mailMarkdown);

        Set<String> mailsOfSpeakers = new HashSet<String>();

        for (Talk talk : Talk.findByStatus(statusTalk)) {
            if (talk.speaker != null && talk.speaker.email != null) {
                mailsOfSpeakers.add(talk.speaker.email);
            }
            for (User coSpeakers : talk.getCoSpeakers()) {
                if (coSpeakers.email != null) {
                    mailsOfSpeakers.add(coSpeakers.email);
                }
            }
        }

        if (!mailsOfSpeakers.isEmpty()) {
            Mail.sendMail(new Mail.Envelop(subjet, mailHtml, mailsOfSpeakers));
        }

        return ok();
    }
}