#include "../include/StompProtocol.h"
#include <iostream>
#include <sstream>

using namespace std;

StompProtocol::StompProtocol(ConnectionHandler* handler) 
    : handler(handler), 
      shouldTerminate(false), 
      isLoggedIn(false),
      subscriptionIdCounter(0), 
      receiptIdCounter(0), 
      topicToSubId(), 
      subIdToTopic(), 
      receiptForCommand() {}


bool StompProtocol::processKeyboard(string commandLine) {
    stringstream ss(commandLine);
    //empty string to hold the first word
    string command;
    //find in the user input till it hits the first space " "
    ss >> command;


    
    if (command == "login"){

        if (isLoggedIn) {
            cout << "The client is already logged in, log out before trying again" << endl;
            return true;
        }
        //same thing read until we hit a space. we assume the command is input in that order so the variables will fill correcrtly
        string hostPort, password;
        ss >> hostPort >> this -> username >> password;

        string frame = "CONNECT\n"
                       "accept-version:1.2\n"
                       "host:stomp.cs.bgu.ac.il\n"
                       "login:" + username + "\n"
                       "passcode:" + password + "\n"
                       "\n"
                       "\u0000";

        handler->sendFrameAscii(frame, '\0');
        return true;
    }else if(command = "join"){

        string topic;
        ss >> topic;
        int subId = subscriptionIdCounter++;
        int receiptId = receiptIdCounter++;


        topicToSubId[topic] = subId;
        subIdToTopic[subId] = topic;


        receiptForCommand[receiptId] = "join " + topic;

        string frame = "SUBSCRIBE\n"
                       "destination:/" + topic + "\n"
                       "id:" + to_string(subId) + "\n"
                       "receipt:" + to_string(receiptId) + "\n"
                       "\n"
                       "\u0000";
        
        handler->sendFrameAscii(frame, '\0');
    }else if(command = "exit"){
        string topic;
        ss >> topic;

        if (topicToSubId.count(topic)){
            int subId = topicToSubId[topic];
            int receiptId = receiptIdCounter++;

            receiptForCommand[receiptId] = "exit " + topic;

            string frame = "UNSUBSCRIBE\n"
                           "id:" + to_string(subId) + "\n"
                           "receipt:" + to_string(receiptId) + "\n"
                           "\n"
                           "\u0000";
            handler->sendFrameAscii(frame, '\0');
        }else{
            cout << "Error: Not subscribed to channel " << topic << endl;
        }
        return true;
    }else if (command = "logout"){
        int receiptID = receiptIdCounter++;
        receiptForCommand[receiptId] = "disconnect";
        string frame = "DISCONNECT\n"
                       "receipt:" + to_string(receiptId) + "\n"
                       "\n"
                       "\u0000";
        handler->sendFrameAscii(frame, '\0');
        return true;
    }
    return true;

}