#include "../include/StompProtocol.h"
#include <iostream>
#include <sstream>
#include <fstream>
using namespace std;

StompProtocol::StompProtocol(ConnectionHandler* handler) 
    : handler(handler), 
      shouldTerminate(false), 
      isLoggedIn(false),
      username(""),
      subscriptionIdCounter(0), 
      receiptIdCounter(0), 
      topicToSubId(), 
      subIdToTopic(), 
      receiptForCommand(),
      gameStorage()
      {}


bool StompProtocol::shouldStop() const {
    return shouldTerminate;
}

void StompProtocol::setShouldTerminate(bool terminate) {
    shouldTerminate = terminate;
}

void StompProtocol::process(string message) {
    stringstream ss(message);
    string command;
    getline(ss, command);

    if (command == "CONNECTED"){
        isLoggedIn = true;
        cout << "Login successful" << endl;
    }

    else if (command == "ERROR") {
        string line;
        bool inBody = false;
        while(getline(ss, line)) {
            if (line.empty() || line == "\r") { inBody = true; continue; }
            if (inBody) cout << line << endl;
        }
        cout << "Error received from server. Disconnecting." << endl;
        shouldTerminate = true;
        isLoggedIn = false;
        handler->close();
    }
    else if (command == "RECEIPT") {
        // Extract receipt-id header
        string line;
        int receiptId = -1;
        while(getline(ss, line)) {
            if (line.find("receipt-id:") == 0) {
                // Parse "receipt-id:123" -> 123
                try {
                    receiptId = stoi(line.substr(11));
                } catch (...) {}
            }
        }

        // Check our local map to see what command this receipt confirms
        if (receiptId != -1 && receiptForCommand.count(receiptId)) {
            string cmd = receiptForCommand[receiptId];
            
            if (cmd == "disconnect") {
                cout << "Disconnected successfully" << endl; // Required output?
                shouldTerminate = true;
                isLoggedIn = false;
                handler->close();
            } 
            else if (cmd.find("join") == 0) {
                // cmd is "join germany_spain"
                cout << "Joined channel " << cmd.substr(5) << endl;
            } 
            else if (cmd.find("exit") == 0) {
                // cmd is "exit germany_spain"
                 cout << "Exited channel " << cmd.substr(5) << endl;
            }
        }
    }
    else if (command == "MESSAGE") {
        // For now, simply print the message body as per requirements
        string line;
        bool inBody = false;
        cout << "Displaying new message:" << endl;
        while(getline(ss, line)) {
             if (line.empty() || line == "\r") { inBody = true; continue; }
             if (inBody) cout << line << endl;
        }
        // TODO: In the future, you may need to parse this body to update gameStorage
        // just like you do in 'report'.
    }
}


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
    }else if(command == "join"){

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
    }else if(command == "exit"){
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
    }else if (command == "report"){
        string filePath;
        ss >> filePath;

        names_and_events nae = parseEventsFile(filePath);

        string gameName = nae.team_a_name + "_" + nae.team_b_name;

        for (const Event& event : nae.events){
            string frame = "SEND\n"
                           "destination:/" + gameName + "\n"
                           "\n" + 
                           "user:" + username + "\n" +
                           "team a:" + nae.team_a_name + "\n" +
                           "team b:" + nae.team_b_name + "\n" +
                           "event name:" + event.get_name() + "\n" +
                           "time:" + std::to_string(event.get_time()) + "\n" +
                           "general game updates:\n";
        
            for (const auto& pair : event.get_game_updates()) {
                    frame += pair.first + ":" + pair.second + "\n";
                }
                
            frame += "team a updates:\n";
            for (const auto& pair : event.get_team_a_updates()) {
                frame += pair.first + ":" + pair.second + "\n";
            }

            frame += "team b updates:\n";
            for (const auto& pair : event.get_team_b_updates()) {
                frame += pair.first + ":" + pair.second + "\n";
            }
                
            frame += "description:\n" + event.get_discription() + "\n" + 
                    "\u0000";

            handler->sendFrameAscii(frame, '\0');

            //make sure we added to the map
            if (gameStorage[gameName].find(username) == gameStorage[gameName].end()) {
                    gameStorage[gameName][username].team_a = nae.team_a_name;
                    gameStorage[gameName][username].team_b = nae.team_b_name;
            }
            for (const auto& pair : event.get_game_updates()) {
                    gameStorage[gameName][username].general_stats[pair.first] = pair.second;
                }
            for (const auto& pair : event.get_team_a_updates()) {
                    gameStorage[gameName][username].team_a_stats[pair.first] = pair.second;
            }
            for (const auto& pair : event.get_team_b_updates()) {
                    gameStorage[gameName][username].team_b_stats[pair.first] = pair.second;
            }
                //save the event
                gameStorage[gameName][username].events.push_back(event);
        }

        return true;


    }else if(command == "summary"){
        string gameName, user, filePath;
        ss >> gameName >> user >> filePath;

        if(gameStorage.count(gameName) && gameStorage[gameName].count(user)){
            const GameState& state = gameStorage[gameName][user];
            std::ofstream outFile(filePath);
            if (outFile.is_open()) {
                outFile << state.team_a << " vs " << state.team_b << "\n";
                outFile << "Game stats:\n";

                outFile << "General stats:\n";
                for (const auto& pair : state.general_stats) {
                     outFile << pair.first << ": " << pair.second << "\n";
                }

                outFile << state.team_a << " stats:\n";
                for (const auto& pair : state.team_a_stats) {
                     outFile << pair.first << ": " << pair.second << "\n";
                }

                outFile << state.team_b << " stats:\n";
                for (const auto& pair : state.team_b_stats) {
                    outFile << pair.first << ": " << pair.second << "\n";
                }

                outFile << "Game event reports:\n";
                for (const Event& ev : state.events) {
                    outFile << ev.get_time() << " - " << ev.get_name() << ":\n\n";
                    outFile << ev.get_discription() << "\n\n\n";
                }

                outFile.close();
                cout << "Summary created: " << filePath << endl; 
             } else {
                 cout << "Error: Could not create file " << filePath << endl;
             }
        } else {
             //no data exists locally
             cout << "No data found for game " << gameName << " from user " << user << endl;
        }

        return true;

    }else if (command == "logout"){
        int receiptId = receiptIdCounter++;
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