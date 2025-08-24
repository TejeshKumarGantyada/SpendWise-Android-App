FROM node:18

WORKDIR /usr/src/app

COPY functions/package*.json ./ 

RUN npm install --only=production

COPY functions ./ 

COPY firebase.json ./ 

CMD ["npm", "start"]
