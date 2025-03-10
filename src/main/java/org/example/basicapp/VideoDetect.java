package org.example.basicapp;

import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.AmazonRekognitionClientBuilder;
import com.amazonaws.services.rekognition.model.CelebrityDetail;
import com.amazonaws.services.rekognition.model.CelebrityRecognition;
import com.amazonaws.services.rekognition.model.CelebrityRecognitionSortBy;
import com.amazonaws.services.rekognition.model.ContentModerationDetection;
import com.amazonaws.services.rekognition.model.ContentModerationSortBy;
import com.amazonaws.services.rekognition.model.Face;
import com.amazonaws.services.rekognition.model.FaceDetection;
import com.amazonaws.services.rekognition.model.FaceMatch;
import com.amazonaws.services.rekognition.model.FaceSearchSortBy;
import com.amazonaws.services.rekognition.model.GetCelebrityRecognitionRequest;
import com.amazonaws.services.rekognition.model.GetCelebrityRecognitionResult;
import com.amazonaws.services.rekognition.model.GetContentModerationRequest;
import com.amazonaws.services.rekognition.model.GetContentModerationResult;
import com.amazonaws.services.rekognition.model.GetFaceDetectionRequest;
import com.amazonaws.services.rekognition.model.GetFaceDetectionResult;
import com.amazonaws.services.rekognition.model.GetFaceSearchRequest;
import com.amazonaws.services.rekognition.model.GetFaceSearchResult;
import com.amazonaws.services.rekognition.model.GetLabelDetectionRequest;
import com.amazonaws.services.rekognition.model.GetLabelDetectionResult;
import com.amazonaws.services.rekognition.model.GetPersonTrackingRequest;
import com.amazonaws.services.rekognition.model.GetPersonTrackingResult;
import com.amazonaws.services.rekognition.model.Instance;
import com.amazonaws.services.rekognition.model.Label;
import com.amazonaws.services.rekognition.model.LabelDetection;
import com.amazonaws.services.rekognition.model.LabelDetectionSortBy;
import com.amazonaws.services.rekognition.model.NotificationChannel;
import com.amazonaws.services.rekognition.model.Parent;
import com.amazonaws.services.rekognition.model.PersonDetection;
import com.amazonaws.services.rekognition.model.PersonMatch;
import com.amazonaws.services.rekognition.model.PersonTrackingSortBy;
import com.amazonaws.services.rekognition.model.S3Object;
import com.amazonaws.services.rekognition.model.StartCelebrityRecognitionRequest;
import com.amazonaws.services.rekognition.model.StartCelebrityRecognitionResult;
import com.amazonaws.services.rekognition.model.StartContentModerationRequest;
import com.amazonaws.services.rekognition.model.StartContentModerationResult;
import com.amazonaws.services.rekognition.model.StartFaceDetectionRequest;
import com.amazonaws.services.rekognition.model.StartFaceDetectionResult;
import com.amazonaws.services.rekognition.model.StartFaceSearchRequest;
import com.amazonaws.services.rekognition.model.StartFaceSearchResult;
import com.amazonaws.services.rekognition.model.StartLabelDetectionRequest;
import com.amazonaws.services.rekognition.model.StartLabelDetectionResult;
import com.amazonaws.services.rekognition.model.StartPersonTrackingRequest;
import com.amazonaws.services.rekognition.model.StartPersonTrackingResult;
import com.amazonaws.services.rekognition.model.Video;
import com.amazonaws.services.rekognition.model.VideoMetadata;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.Message;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class VideoDetect {

  private static String bucket = "nessathon";
  private static String video = "thane_station.mp4"; 
  private static String queueUrl =  "https://sqs.ap-south-1.amazonaws.com/575333762837/VideoQueue";
  private static String topicArn="arn:aws:sns:ap-south-1:575333762837:AmazonRekognition";
  private static String roleArn="arn:aws:iam::575333762837:role/Rekognition-Role";
    
  private static AmazonSQS sqs = null;
  private static AmazonRekognition rek = null;
  
  private static NotificationChannel channel= new NotificationChannel()
          .withSNSTopicArn(topicArn)
          .withRoleArn(roleArn);

  private static Map<Face, List<Long>> faceTimeMap = new HashMap<Face, List<Long>>();
  private static Map<Face, String> faceMatchingMap = new HashMap<Face, String>();
  private static Set<Long> frameSecondsDetails = new HashSet<Long>();
  private static String startJobId = null;


  public static void main(String[] args)  throws Exception {
	  Instant start = Instant.now();
	  System.out.println("**************************************************************************************************");
	  System.out.println("************************* Starting Face Recognition *********************************************");
      sqs = AmazonSQSClientBuilder.defaultClient();
      rek = AmazonRekognitionClientBuilder.defaultClient();

      //=================================================
      StartFaceSearchCollection(bucket,video);
      //=================================================
      //Poll queue for messages
      List<Message> messages=null;
      int dotLine=0;
      boolean jobFound=false;

      //loop until the job status is published. Ignore other messages in queue.
      do{
          messages = sqs.receiveMessage(queueUrl).getMessages();
          if (dotLine++<20){
              System.out.print(".");
          }else{
              System.out.println();
              dotLine=0;
          }

          if (!messages.isEmpty()) {
              //Loop through messages received.
              for (Message message: messages) {
                  String notification = message.getBody();

                  // Get status and job id from notification.
                  ObjectMapper mapper = new ObjectMapper();
                  JsonNode jsonMessageTree = mapper.readTree(notification);
                  JsonNode messageBodyText = jsonMessageTree.get("Message");
                  ObjectMapper operationResultMapper = new ObjectMapper();
                  JsonNode jsonResultTree = operationResultMapper.readTree(messageBodyText.textValue());
                  JsonNode operationJobId = jsonResultTree.get("JobId");
                  JsonNode operationStatus = jsonResultTree.get("Status");
                  System.out.println("****** Job finished *******");
                  if(operationJobId.asText().equals(startJobId)){
                      jobFound=true;
                      if (operationStatus.asText().equals("SUCCEEDED")){
                          //============================================
                    	  GetResultsFaceSearchCollection();
                          //============================================
                      }
                      else{
                          System.out.println("Video analysis failed");
                      }

                      sqs.deleteMessage(queueUrl,message.getReceiptHandle());
                  }

                  else{
                      System.out.println("Job received was not job " +  startJobId);
                      //Delete unknown message. Consider moving message to dead letter queue
                      sqs.deleteMessage(queueUrl,message.getReceiptHandle());
                  }
              }
          }
      } while (!jobFound);

      procesFaceMap();
      sliptFrames();
      
      System.out.println(Duration.between(start, Instant.now()));
  }


	private static void sliptFrames() {
		//DecodeAndCaptureFrames.processFrames(frameSecondsDetails);
	}


	private static void procesFaceMap() {
		System.out.println("**************************************************************************************************");
		System.out.println("****************************** FACE SEARCH RESULT ************************************************");
		if (faceTimeMap.isEmpty()) {
			System.out.println("No face Matched");
		} else {
			for (Map.Entry<Face, List<Long>> entry : faceTimeMap.entrySet()) {
				System.out.println("Face Matching with " + entry.getKey().getExternalImageId() 
						+ " with Similarity percentage " + faceMatchingMap.get(entry.getKey()));
				System.out.println(entry.getKey().getExternalImageId() + " Seen at ");
				Set<String> hs = new HashSet<>();
				for (Long tempMillis : entry.getValue()) {
					long minutes = (tempMillis / 1000) / 60;
					long seconds = (tempMillis / 1000) % 60;
					frameSecondsDetails.add(seconds);
					String stimestamp = "" + minutes + "minutes and " + seconds + " seconds.";
					hs.add(stimestamp);
				}
				hs.forEach(System.out::println);
			}
		}
		System.out.println("****************************** END ************************************************");
		System.out.println("**************************************************************************************************");
		
	}


private static void StartLabels(String bucket, String video) throws Exception{

      StartLabelDetectionRequest req = new StartLabelDetectionRequest()
              .withVideo(new Video()
                      .withS3Object(new S3Object()
                              .withBucket(bucket)
                              .withName(video)))
              .withMinConfidence(50F)
              .withJobTag("DetectingLabels")
              .withNotificationChannel(channel);

      StartLabelDetectionResult startLabelDetectionResult = rek.startLabelDetection(req);
      startJobId=startLabelDetectionResult.getJobId();
      
      
  }
  
  

  private static void GetResultsLabels() throws Exception{

      int maxResults=10;
      String paginationToken=null;
      GetLabelDetectionResult labelDetectionResult=null;

      do {
          if (labelDetectionResult !=null){
              paginationToken = labelDetectionResult.getNextToken();
          }

          GetLabelDetectionRequest labelDetectionRequest= new GetLabelDetectionRequest()
                  .withJobId(startJobId)
                  .withSortBy(LabelDetectionSortBy.TIMESTAMP)
                  .withMaxResults(maxResults)
                  .withNextToken(paginationToken);


          labelDetectionResult = rek.getLabelDetection(labelDetectionRequest);

          VideoMetadata videoMetaData=labelDetectionResult.getVideoMetadata();

          System.out.println("Format: " + videoMetaData.getFormat());
          System.out.println("Codec: " + videoMetaData.getCodec());
          System.out.println("Duration: " + videoMetaData.getDurationMillis());
          System.out.println("FrameRate: " + videoMetaData.getFrameRate());


          //Show labels, confidence and detection times
          List<LabelDetection> detectedLabels= labelDetectionResult.getLabels();

          for (LabelDetection detectedLabel: detectedLabels) {
              long seconds=detectedLabel.getTimestamp();
              Label label=detectedLabel.getLabel();
              System.out.println("Millisecond: " + Long.toString(seconds) + " ");
              
              System.out.println("   Label:" + label.getName()); 
              System.out.println("   Confidence:" + detectedLabel.getLabel().getConfidence().toString());
    
              List<Instance> instances = label.getInstances();
              System.out.println("   Instances of " + label.getName());
              if (instances.isEmpty()) {
                  System.out.println("        " + "None");
              } else {
                  for (Instance instance : instances) {
                      System.out.println("        Confidence: " + instance.getConfidence().toString());
                      System.out.println("        Bounding box: " + instance.getBoundingBox().toString());
                  }
              }
              System.out.println("   Parent labels for " + label.getName() + ":");
              List<Parent> parents = label.getParents();
              if (parents.isEmpty()) {
                  System.out.println("        None");
              } else {
                  for (Parent parent : parents) {
                      System.out.println("        " + parent.getName());
                  }
              }
              System.out.println();
          }
      } while (labelDetectionResult !=null && labelDetectionResult.getNextToken() != null);
  }
 
    private static void StartFaceSearchCollection(String bucket, String video) throws Exception{

        StartFaceSearchRequest req = new StartFaceSearchRequest()
                .withCollectionId("suspectCollection")
                .withVideo(new Video()
                        .withS3Object(new S3Object()
                                .withBucket(bucket)
                                .withName(video)))
                .withNotificationChannel(channel);

        StartFaceSearchResult startPersonCollectionSearchResult = rek.startFaceSearch(req);
        startJobId=startPersonCollectionSearchResult.getJobId();
    } 

    private static void GetResultsFaceSearchCollection() throws Exception{
    	faceTimeMap.clear();
       GetFaceSearchResult faceSearchResult=null;
       int maxResults=10;
       String paginationToken=null;

       do {

           if (faceSearchResult !=null){
               paginationToken = faceSearchResult.getNextToken();
           }


           faceSearchResult  = rek.getFaceSearch(
                   new GetFaceSearchRequest()
                   .withJobId(startJobId)
                   .withMaxResults(maxResults)
                   .withNextToken(paginationToken)
                   .withSortBy(FaceSearchSortBy.TIMESTAMP)
                   );

           //Show search results
           List<PersonMatch> matches= 
                   faceSearchResult.getPersons();

           
           for (PersonMatch match: matches) { 
               long milliSeconds=match.getTimestamp();
               List <FaceMatch> faceMatches = match.getFaceMatches();
               if (faceMatches != null) {
                   for (FaceMatch faceMatch: faceMatches){
                       Face face=faceMatch.getFace();
                       faceMatchingMap.put(face, faceMatch.getSimilarity().toString());
                       if(faceTimeMap.containsKey(face)) {
                    	   List<Long> tempList = faceTimeMap.get(face);
                    	   tempList.add(match.getTimestamp());
                    	   faceTimeMap.put(face, tempList);
                       }else {
                    	   List<Long> tempList = new ArrayList<Long>();
                    	   tempList.add(match.getTimestamp());
                    	   faceTimeMap.put(face, tempList);
                       }
                   }
               }
           } 
       } while (faceSearchResult !=null && faceSearchResult.getNextToken() != null);

   }
}