FROM node:18

WORKDIR /usr/src/app

COPY function/package*.json ./ 

RUN npm install --only=production

COPY function ./ 

COPY firebase.json ./ 

CMD ["npm", "start"]
